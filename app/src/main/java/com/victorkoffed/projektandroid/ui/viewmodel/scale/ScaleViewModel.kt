package com.victorkoffed.projektandroid.ui.viewmodel.scale

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.util.Log
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.victorkoffed.projektandroid.data.db.Brew
import com.victorkoffed.projektandroid.data.db.BrewSample
import com.victorkoffed.projektandroid.data.repository.CoffeeRepository
import com.victorkoffed.projektandroid.data.repository.ScaleRepository
import com.victorkoffed.projektandroid.domain.model.BleConnectionState
import com.victorkoffed.projektandroid.domain.model.DiscoveredDevice
import com.victorkoffed.projektandroid.domain.model.ScaleMeasurement
import com.victorkoffed.projektandroid.ui.viewmodel.brew.BrewSetupState
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

// Konstanter för SharedPreferences
private const val PREFS_NAME = "ScalePrefs"
// Nyckel för att spara den senast anslutna vågens adress
private const val PREF_REMEMBERED_SCALE_ADDRESS = "remembered_scale_address"
// Nyckel för att spara användarens val att komma ihåg vågen
private const val PREF_REMEMBER_SCALE_ENABLED = "remember_scale_enabled"

class ScaleViewModel : AndroidViewModel {

    private val scaleRepo: ScaleRepository
    private val coffeeRepo: CoffeeRepository
    private val sharedPreferences: SharedPreferences

    // Flaggor för att skilja på avsiktlig (manuell) och oavsiktlig (fel/tappad signal) frånkoppling.
    private var isManualDisconnect = false

    constructor(app: Application, scaleRepo: ScaleRepository, coffeeRepo: CoffeeRepository) : super(
        app
    ) {
        this.scaleRepo = scaleRepo
        this.coffeeRepo = coffeeRepo
        // Initiera SharedPreferences för att spara preferenser som anslutna vågar
        this.sharedPreferences = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        this._devices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
        this.devices = _devices
        this._isScanning = MutableStateFlow(false)
        this.isScanning = _isScanning

        // Observerar anslutningsstatus från repository. Använder SharedFlow för att flera
        // subskriberare (UI och ViewModel-logik) kan dela samma flöde och för att det är mer
        // lämpligt för händelser som kan behöva återstartas (t.ex. vid återanslutning).
        this.connectionState = scaleRepo.observeConnectionState()
            .onEach { state -> // Lyssna på state-ändringar för att hantera anslutningslogik (auto-återanslutning, etc.)
                handleConnectionStateChange(state)
            }
            .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)


        this._rawMeasurement = MutableStateFlow(ScaleMeasurement(0.0f, 0.0f))
        // _tareOffset håller vikten vid det ögonblick tarering utfördes
        this._tareOffset = MutableStateFlow(0.0f)

        // Kombinerar rådata och tareringsoffset för att beräkna den faktiska (tarerade) vikten.
        this.measurement = combine(_rawMeasurement, _tareOffset) { raw, offset ->
            ScaleMeasurement(
                weightGrams = raw.weightGrams - offset,
                // Flödeshastigheten (flow rate) påverkas inte av tarering
                flowRateGramsPerSecond = raw.flowRateGramsPerSecond
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ScaleMeasurement(0.0f, 0.0f)
        )


        this._error = MutableStateFlow<String?>(null)
        this.error = _error
        this._isRecording = MutableStateFlow(false)
        this.isRecording = _isRecording
        this._isPaused = MutableStateFlow(false)
        this.isPaused = _isPaused
        this._recordedSamplesFlow = MutableStateFlow<List<BrewSample>>(emptyList())
        this.recordedSamplesFlow = _recordedSamplesFlow
        this._recordingTimeMillis = MutableStateFlow(0L)
        this.recordingTimeMillis = _recordingTimeMillis
        this._weightAtPause = MutableStateFlow<Float?>(null)
        this.weightAtPause = _weightAtPause
        // State för att visa nedräkning innan inspelning startar
        this._countdown = MutableStateFlow<Int?>(null)
        this.countdown = _countdown

        // Logik för att försöka återansluta till en sparad våg vid ViewModel-initiering
        attemptAutoConnect()
    }

    // --- Scanning State ---
    private val _devices: MutableStateFlow<List<DiscoveredDevice>>
    val devices: StateFlow<List<DiscoveredDevice>>
    private val _isScanning: MutableStateFlow<Boolean>
    val isScanning: StateFlow<Boolean>
    private var scanJob: Job? = null

    // --- Connection and Measurement State ---
    // Används för att hantera anslutningsstatus och återanslutningslogik
    val connectionState: SharedFlow<BleConnectionState>
    // Rådata direkt från vågen (innan tarering)
    private val _rawMeasurement: MutableStateFlow<ScaleMeasurement>
    // Tareringsoffset
    private val _tareOffset: MutableStateFlow<Float>
    // Tarerad och flödeshastighets-beräknad mätning (exponeras för UI)
    val measurement: StateFlow<ScaleMeasurement>

    // --- Error State ---
    private val _error: MutableStateFlow<String?>
    val error: StateFlow<String?>

    // --- Recording State ---
    private val _isRecording: MutableStateFlow<Boolean>
    val isRecording: StateFlow<Boolean>
    private val _isPaused: MutableStateFlow<Boolean>
    val isPaused: StateFlow<Boolean>
    private val _recordedSamplesFlow: MutableStateFlow<List<BrewSample>>
    val recordedSamplesFlow: StateFlow<List<BrewSample>>
    private val _recordingTimeMillis: MutableStateFlow<Long>
    val recordingTimeMillis: StateFlow<Long>
    private val _weightAtPause: MutableStateFlow<Float?>
    val weightAtPause: StateFlow<Float?>
    private val _countdown: MutableStateFlow<Int?>
    val countdown: StateFlow<Int?>

    private var recordingStartTime: Long = 0L
    private var timePausedAt: Long = 0L
    // Jobb för att prenumerera på mätningar från repository
    private var measurementJob: Job? = null
    // Jobb för att uppdatera inspelningstiden (timer)
    private var timerJob: Job? = null
    // --- End Recording State ---

    // ---------------------------------------------------------------------------------------------
    // --- Funktionalitet för Skanning och Anslutning ---
    // ---------------------------------------------------------------------------------------------

    /**
     * Startar skanning efter Bluetooth-enheter med en timeout på 5 sekunder.
     * Samlar in och uppdaterar listan av hittade enheter i `_devices`.
     */
    fun startScan() {
        if (_isScanning.value) return

        _devices.value = emptyList()
        _error.value = null
        _isScanning.value = true

        scanJob?.cancel()

        scanJob = viewModelScope.launch {
            try {
                // Sätter en tidsgräns för skanningen
                withTimeoutOrNull(5.seconds) {
                    scaleRepo.startScanDevices()
                        .catch { e ->
                            _error.value = e.message ?: "Okänt skanningsfel"
                            _isScanning.value = false
                        }
                        .collect { list ->
                            _devices.value = list
                        }
                }
            } finally {
                // Säkerställer att _isScanning sätts till false när jobbet avslutas av någon anledning (timeout, cancel, complete)
                if (_isScanning.value) {
                    _isScanning.value = false
                    Log.d("ScaleViewModel", "Skanning avslutad (timeout eller manuell).")
                }
            }
        }
    }

    /**
     * Stoppar pågående skanning manuellt.
     */
    fun stopScan() {
        if (scanJob?.isActive == true) {
            scanJob?.cancel()
            Log.d("ScaleViewModel", "Skanning stoppades manuellt av användaren.")
        }
    }

    /**
     * Ansluter till den valda enheten och nollställer tareringsoffset.
     * isManualDisconnect flaggan nollställs inför ett nytt anslutningsförsök.
     */
    fun connect(device: DiscoveredDevice) {
        stopScan() // Stoppa skanning innan anslutning
        _tareOffset.value = 0.0f
        isManualDisconnect = false
        scaleRepo.connect(device.address)
    }

    /**
     * Utför manuell frånkoppling från vågen.
     * Sätter flaggan `isManualDisconnect` för att förhindra auto-återanslutning.
     */
    fun disconnect() {
        Log.d("ScaleViewModel", "Manuell frånkoppling initierad.")
        isManualDisconnect = true // Markera att det är en avsiktlig frånkoppling
        stopRecording() // Stoppa eventuell inspelning vid frånkoppling
        measurementJob?.cancel(); measurementJob = null // Avbryt mätjobbet
        scaleRepo.disconnect()
        _tareOffset.value = 0.0f
    }

    /**
     * Tarerar vågen genom att sätta `_tareOffset` till den aktuella råa vikten.
     * Lägger till en samplingspunkt vid tareringstillfället om inspelning pågår.
     */
    fun tareScale() {
        scaleRepo.tareScale()
        _tareOffset.value = _rawMeasurement.value.weightGrams
        if (_isRecording.value && !_isPaused.value) {
            addSamplePoint(measurement.value)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // --- Inspelningsfunktionalitet (Brew Recording) ---
    // ---------------------------------------------------------------------------------------------

    /**
     * Startar inspelningssekvensen med en 3-sekunders nedräkning, följt av tarering.
     */
    fun startRecording() {
        if (_isRecording.value || _isPaused.value || _countdown.value != null) { Log.w("ScaleViewModel", "Start inspelning anropades men är redan upptagen."); return }
        if (connectionState.replayCache.lastOrNull() !is BleConnectionState.Connected) { _error.value = "Kan inte starta inspelning, vågen är inte ansluten."; return }
        viewModelScope.launch {
            try {
                Log.d("ScaleViewModel", "Initierar inspelningssekvens...")
                tareScale()
                // Nedräkning
                _countdown.update { 3 }; delay(1000L)
                _countdown.update { 2 }; delay(1000L)
                _countdown.update { 1 }; delay(1000L)
                _countdown.update { null }
                Log.d("ScaleViewModel", "Nedräkning klar. Startar inspelning.")
                internalStartRecording()
            } catch (e: Exception) { Log.e("ScaleViewModel", "Fel under inspelningsinitiering", e); _countdown.value = null; _error.value = "Kunde inte starta inspelning." }
        }
    }
    private fun internalStartRecording() {
        _recordedSamplesFlow.value = emptyList(); _recordingTimeMillis.value = 0L; _isPaused.value = false; _weightAtPause.value = null; recordingStartTime = SystemClock.elapsedRealtime(); _isRecording.value = true; addSamplePoint(measurement.value); startTimer()
    }
    fun pauseRecording() { if (!_isRecording.value || _isPaused.value) return; _isPaused.value = true; timePausedAt = SystemClock.elapsedRealtime(); _weightAtPause.value = measurement.value.weightGrams; timerJob?.cancel() }
    fun resumeRecording() { if (!_isRecording.value || !_isPaused.value) return; val pauseDuration = SystemClock.elapsedRealtime() - timePausedAt; recordingStartTime += pauseDuration; _isPaused.value = false; _weightAtPause.value = null; startTimer() }
    /**
     * Stoppar inspelningen, rensar variabler och försöker spara bryggningen och dess mätpunkter till databasen.
     */
    suspend fun stopRecordingAndSave(setupState: BrewSetupState): Long? {
        if (_countdown.value != null) { stopRecording(); return null } // Avbryt om nedräkning pågår
        if (!_isRecording.value && !_isPaused.value) return null
        val finalTimeMillis = _recordingTimeMillis.value; val finalSamples = _recordedSamplesFlow.value; stopRecording()
        if (finalSamples.isEmpty()) { _error.value = "Ingen data spelades in."; return null }
        val actualStartTimeMillis = System.currentTimeMillis() - finalTimeMillis; val beanId = setupState.selectedBean?.id; val doseGrams = setupState.doseGrams.toDoubleOrNull()
        if (beanId == null || doseGrams == null) { _error.value = "Saknar bönor eller dos för att spara."; return null }
        // Skapa det nya Brew-objektet
        val newBrew = Brew(beanId = beanId, doseGrams = doseGrams, startedAt = Date(actualStartTimeMillis), grinderId = setupState.selectedGrinder?.id, methodId = setupState.selectedMethod?.id, grindSetting = setupState.grindSetting.takeIf { it.isNotBlank() }, grindSpeedRpm = setupState.grindSpeedRpm.toDoubleOrNull(), brewTempCelsius = setupState.brewTempCelsius.toDoubleOrNull(), notes = "Inspelad från våg ${Date()}")
        // Asynkron insättning i databasen
        val result = viewModelScope.async { try { val newId = coffeeRepo.addBrewWithSamples(newBrew, finalSamples); _error.value = null; newId } catch (e: Exception) { _error.value = "Kunde inte spara bryggning: ${e.message}"; Log.e("ScaleViewModel", "Fel vid sparning av bryggning: ${e.message}", e); null } }; return result.await()
    }
    /**
     * Stoppar inspelning och nollställer alla relaterade states.
     */
    fun stopRecording() { _countdown.value = null; _isRecording.value = false; _isPaused.value = false; _weightAtPause.value = null; timerJob?.cancel(); _recordedSamplesFlow.value = emptyList(); _recordingTimeMillis.value = 0L }
    private fun startTimer() { timerJob?.cancel(); timerJob = viewModelScope.launch { while (_isRecording.value && !_isPaused.value) { _recordingTimeMillis.value = SystemClock.elapsedRealtime() - recordingStartTime; delay(100) } } }
    /**
     * Skapar en ny `BrewSample` baserat på den aktuella mätningen och lägger till den i listan.
     */
    private fun addSamplePoint(measurement: ScaleMeasurement) { if (!_isRecording.value || _isPaused.value) return; val elapsedTimeMs = SystemClock.elapsedRealtime() - recordingStartTime; val newSample = BrewSample(brewId = 0, timeMillis = elapsedTimeMs, massGrams = String.format(Locale.US, "%.1f", measurement.weightGrams).toDouble(), flowRateGramsPerSecond = String.format(Locale.US, "%.1f", measurement.flowRateGramsPerSecond).toDouble()); _recordedSamplesFlow.value = _recordedSamplesFlow.value + newSample }


    // ---------------------------------------------------------------------------------------------
    // --- Funktionalitet för 'Kom Ihåg Våg' Preferens ---
    // ---------------------------------------------------------------------------------------------

    /**
     * Hämtar om automatisk anslutning till våg är aktiverad i preferenserna.
     */
    fun isRememberScaleEnabled(): Boolean {
        return sharedPreferences.getBoolean(PREF_REMEMBER_SCALE_ENABLED, false)
    }

    /**
     * Uppdaterar preferensen och hanterar lagringen av den sparade adressen.
     * Om 'enabled' blir false rensas den sparade adressen.
     */
    fun setRememberScaleEnabled(enabled: Boolean) {
        sharedPreferences.edit { putBoolean(PREF_REMEMBER_SCALE_ENABLED, enabled) }
        Log.d("ScaleViewModel", "Ställ in 'kom ihåg våg' till: $enabled")
        if (!enabled) {
            // Om användaren avmarkerar, glöm den sparade adressen
            saveRememberedScaleAddress(null)
        } else {
            // Om användaren markerar och vi är anslutna, spara den nuvarande adressen
            val currentState = connectionState.replayCache.lastOrNull()
            if (currentState is BleConnectionState.Connected) {
                // Sparar enhetens namn/adress för återanslutning.
                saveRememberedScaleAddress(currentState.deviceName)
            }
        }
    }

    /**
     * Sparar den anslutna vågens adress om 'remember' är aktiverat, annars rensas den.
     * Använder null för att rensa.
     */
    private fun saveRememberedScaleAddress(address: String?) {
        if (address != null && isRememberScaleEnabled()) {
            sharedPreferences.edit { putString(PREF_REMEMBERED_SCALE_ADDRESS, address) }
            Log.d("ScaleViewModel", "Sparade vågadress: $address")
        } else {
            sharedPreferences.edit { remove(PREF_REMEMBERED_SCALE_ADDRESS) }
            Log.d("ScaleViewModel", "'Kom ihåg våg' inaktiverat eller rensat sparad adress.")
        }
    }

    /**
     * Laddar den sparade vågadressen från preferenserna, förutsatt att 'remember' är aktiverat.
     */
    private fun loadRememberedScaleAddress(): String? {
        return if (isRememberScaleEnabled()) {
            sharedPreferences.getString(PREF_REMEMBERED_SCALE_ADDRESS, null)
        } else {
            null
        }
    }

    /**
     * Försöker ansluta automatiskt till en sparad våg baserat på preferenserna.
     */
    private fun attemptAutoConnect() {
        if (!isRememberScaleEnabled()) {
            Log.d("ScaleViewModel", "'Kom ihåg våg' är inaktiverat, hoppar över auto-anslutning.")
            return
        }

        if (connectionState.replayCache.lastOrNull() is BleConnectionState.Connected) {
            Log.d("ScaleViewModel", "Redan ansluten, hoppar över auto-anslutning.")
            return
        }
        val rememberedAddress = loadRememberedScaleAddress()
        if (rememberedAddress != null) {
            Log.d("ScaleViewModel", "Försöker auto-ansluta till: $rememberedAddress")
            isManualDisconnect = false
            // Vi använder adressen/namnet för att försöka ansluta. Name sätts till null då vi inte vet det här.
            val dummyDevice = DiscoveredDevice(name = null, address = rememberedAddress, rssi = 0)
            connect(dummyDevice)
        } else {
            Log.d("ScaleViewModel", "Ingen sparad vågadress hittades.")
        }
    }

    // ---------------------------------------------------------------------------------------------
    // --- Hantering av Anslutningsstatus ---
    // ---------------------------------------------------------------------------------------------

    /**
     * Hanterar ändringar i Bluetooth-anslutningsstatus. Inkluderar start av mätjobb,
     * lagring av sparad våg och logik för auto-återanslutning.
     */
    private fun handleConnectionStateChange(state: BleConnectionState) {
        when (state) {
            is BleConnectionState.Connected -> {
                Log.d("ScaleViewModel", "Ansluten till ${state.deviceName}.")
                // Spara adressen FÖRST när anslutningen lyckats
                if (isRememberScaleEnabled()) {
                    saveRememberedScaleAddress(state.deviceName)
                }
                isManualDisconnect = false // Nollställ efter lyckad anslutning

                // Starta mätjobbet FÖRST efter lyckad anslutning
                if (measurementJob?.isActive != true) {
                    measurementJob = viewModelScope.launch {
                        scaleRepo.observeMeasurements().collect { rawData ->
                            _rawMeasurement.value = rawData
                            // Lägg till samplingspunkt om inspelning pågår
                            if (_isRecording.value && !_isPaused.value) { addSamplePoint(measurement.value) }
                        }
                    }
                }
            }
            is BleConnectionState.Disconnected -> {
                Log.d("ScaleViewModel", "Frånkopplad. Manuell frånkoppling-flagga: $isManualDisconnect")
                measurementJob?.cancel(); measurementJob = null

                // Försök återansluta ENDAST om det INTE var en manuell frånkoppling OCH 'remember' är aktivt
                if (!isManualDisconnect && isRememberScaleEnabled()) {
                    viewModelScope.launch {
                        Log.d("ScaleViewModel", "Oväntad frånkoppling. Väntar 2 sekunder före försök till auto-återanslutning...")
                        delay(2000L)
                        // Dubbelkolla att vi fortfarande är frånkopplade innan återanslutningsförsök
                        if (connectionState.replayCache.lastOrNull() is BleConnectionState.Disconnected) {
                            Log.d("ScaleViewModel", "Fortfarande frånkopplad. Försöker auto-återanslutning.")
                            attemptAutoConnect()
                        } else {
                            Log.d("ScaleViewModel", "Status ändrades under fördröjningen, hoppar över auto-återanslutning.")
                        }
                    }
                }
                isManualDisconnect = false // Återställ efter hantering
            }
            is BleConnectionState.Error -> {
                Log.e("ScaleViewModel", "Anslutningsfel: ${state.message}")
                measurementJob?.cancel(); measurementJob = null
                _error.value = "Anslutningsfel: ${state.message}"
                isManualDisconnect = false
            }
            is BleConnectionState.Connecting -> {
                Log.d("ScaleViewModel", "Ansluter...")
            }
        }
    }

    /**
     * Nollställer felmeddelandet efter att det har visats för användaren.
     */
    fun clearError() {
        _error.value = null
    }
}