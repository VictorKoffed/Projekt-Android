package com.victorkoffed.projektandroid.ui.viewmodel.scale

// --- KONTROLLERA ATT ALLA DESSA IMPORTER FINNS ---
import android.app.Application
import android.content.Context // <-- NY IMPORT för SharedPreferences
import android.content.SharedPreferences // <-- NY IMPORT för SharedPreferences
import android.os.SystemClock
import android.util.Log // För loggning
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
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.seconds
// --- SLUT PÅ IMPORTER ---

// --- NYA KONSTANTER ---
private const val PREFS_NAME = "ScalePrefs"
private const val PREF_REMEMBERED_SCALE_ADDRESS = "remembered_scale_address"
// --- NY KONSTANT FÖR REMEMBER-VAL ---
private const val PREF_REMEMBER_SCALE_ENABLED = "remember_scale_enabled"
// --- SLUT NYA KONSTANTER ---

class ScaleViewModel : AndroidViewModel {

    private val scaleRepo: ScaleRepository
    private val coffeeRepo: CoffeeRepository
    // --- NYTT: SharedPreferences ---
    private val sharedPreferences: SharedPreferences
    // --- SLUT NYTT ---

    // --- NYTT: Flagga för manuell disconnect ---
    private var isManualDisconnect = false
    // --- SLUT NYTT ---


    constructor(app: Application, scaleRepo: ScaleRepository, coffeeRepo: CoffeeRepository) : super(
        app
    ) {
        this.scaleRepo = scaleRepo
        this.coffeeRepo = coffeeRepo
        // --- NY RAD: Initiera SharedPreferences ---
        this.sharedPreferences = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        this._devices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
        this.devices = _devices
        this._isScanning = MutableStateFlow(false)
        this.isScanning = _isScanning
        // --- ÄNDRAD RAD: Använd shareIn istället för stateIn för att kunna återstarta ---
        this.connectionState = scaleRepo.observeConnectionState()
            .onEach { state -> // Lyssna på state-ändringar här
                handleConnectionStateChange(state)
            }
            .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1) // shareIn istället för stateIn


        this._rawMeasurement = MutableStateFlow(ScaleMeasurement(0.0f, 0.0f))
        this._tareOffset = MutableStateFlow(0.0f)

        // --- ÄNDRAD RAD: Använd combine med den delade connectionState ---
        this.measurement = combine(_rawMeasurement, _tareOffset) { raw, offset ->
            ScaleMeasurement(
                weightGrams = raw.weightGrams - offset,
                flowRateGramsPerSecond = raw.flowRateGramsPerSecond // Flödet påverkas inte av tarering
            )
            // Använd stateIn här eftersom measurement inte behöver återstartas på samma sätt
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
        // --- NY RAD NEDAN ---
        this._countdown = MutableStateFlow<Int?>(null)
        this.countdown = _countdown

        // --- NYTT: Försök auto-ansluta vid start (kollar nu preferens) ---
        attemptAutoConnect()
        // --- SLUT NYTT ---
    }

    // --- Scanning State ---
    private val _devices: MutableStateFlow<List<DiscoveredDevice>>
    val devices: StateFlow<List<DiscoveredDevice>>
    private val _isScanning: MutableStateFlow<Boolean>
    val isScanning: StateFlow<Boolean>
    private var scanJob: Job? = null

    // --- Connection and Measurement State ---
    // Ändrad till SharedFlow för att hantera återanslutning
    val connectionState: SharedFlow<BleConnectionState>
    private val _rawMeasurement: MutableStateFlow<ScaleMeasurement>
    private val _tareOffset: MutableStateFlow<Float>

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
    // --- NYTT STATE FÖR NEDRÄKNING ---
    private val _countdown: MutableStateFlow<Int?>
    val countdown: StateFlow<Int?>
    // --- SLUT NYTT STATE ---


    private var recordingStartTime: Long = 0L
    private var timePausedAt: Long = 0L
    private var measurementJob: Job? = null
    private var timerJob: Job? = null
    // --- End Recording State ---

    // --- Funktioner ---
    fun startScan() {
        if (_isScanning.value) return

        _devices.value = emptyList()
        _error.value = null
        _isScanning.value = true

        scanJob?.cancel() // Avbryt tidigare jobb

        scanJob = viewModelScope.launch {
            try {
                withTimeoutOrNull(5.seconds) { // Timeout efter 5 sekunder
                    scaleRepo.startScanDevices()
                        .catch { e ->
                            _error.value = e.message ?: "Unknown scan error"
                            _isScanning.value = false // Säkerställ att vi slutar vid fel
                        }
                        .collect { list ->
                            _devices.value = list
                        }
                }
            } finally {
                // Detta körs alltid när jobbet slutar (timeout, cancel, error, complete)
                if (_isScanning.value) { // Sätt bara till false om vi fortfarande trodde vi skannade
                    _isScanning.value = false
                    Log.d("ScaleViewModel", "Scanning stopped (timeout or manual).")
                    if (_devices.value.isEmpty() && _error.value == null) {
                        Log.d("ScaleViewModel", "No devices found during scan.")
                    }
                }
            }
        }
    }

    fun stopScan() {
        if (scanJob?.isActive == true) {
            scanJob?.cancel()
            Log.d("ScaleViewModel", "Scanning manually stopped by user.")
            // _isScanning sätts till false i 'finally' i startScan
        }
    }

    fun connect(device: DiscoveredDevice) {
        stopScan() // Stoppa skanning innan anslutning
        _tareOffset.value = 0.0f
        isManualDisconnect = false // Nollställ flaggan vid nytt anslutningsförsök
        scaleRepo.connect(device.address) // Starta anslutningen
        // measurementJob startas nu i handleConnectionStateChange när Connected inträffar
    }

    // --- ÄNDRAD disconnect FUNKTION ---
    fun disconnect() {
        Log.d("ScaleViewModel", "Manual disconnect initiated.")
        isManualDisconnect = true // Sätt flaggan FÖRE anrop till scaleRepo
        stopRecording() // Stoppa ev. inspelning vid frånkoppling
        measurementJob?.cancel(); measurementJob = null
        scaleRepo.disconnect() // Anropa disconnect i repository/client
        _tareOffset.value = 0.0f
        // Rensa INTE sparad adress här automatiskt. Det görs om användaren avmarkerar checkboxen.
    }
    // --- SLUT ÄNDRAD FUNKTION ---


    fun tareScale() {
        scaleRepo.tareScale()
        // Uppdatera offset baserat på den *råa* mätningen vid tareringstillfället
        _tareOffset.value = _rawMeasurement.value.weightGrams
        // Om inspelning pågår, lägg till en punkt med det nya (nära noll) värdet
        if (_isRecording.value && !_isPaused.value) {
            addSamplePoint(measurement.value)
        }
    }

    // --- Recording Functions (oförändrade från tidigare) ---
    fun startRecording() {
        if (_isRecording.value || _isPaused.value || _countdown.value != null) { Log.w("ScaleViewModel", "Start recording called but already busy."); return }
        if (connectionState.replayCache.lastOrNull() !is BleConnectionState.Connected) { _error.value = "Cannot start recording, scale is not connected."; return }
        viewModelScope.launch {
            try {
                Log.d("ScaleViewModel", "Initiating recording sequence...")
                tareScale()
                _countdown.value = 3; delay(1000L)
                _countdown.value = 2; delay(1000L)
                _countdown.value = 1; delay(1000L)
                _countdown.value = null
                Log.d("ScaleViewModel", "Countdown finished. Starting recording.")
                internalStartRecording()
            } catch (e: Exception) { Log.e("ScaleViewModel", "Error during recording initiation", e); _countdown.value = null; _error.value = "Failed to start recording." }
        }
    }
    private fun internalStartRecording() {
        _recordedSamplesFlow.value = emptyList(); _recordingTimeMillis.value = 0L; _isPaused.value = false; _weightAtPause.value = null; recordingStartTime = SystemClock.elapsedRealtime(); _isRecording.value = true; addSamplePoint(measurement.value); startTimer()
    }
    fun pauseRecording() { if (!_isRecording.value || _isPaused.value) return; _isPaused.value = true; timePausedAt = SystemClock.elapsedRealtime(); _weightAtPause.value = measurement.value.weightGrams; timerJob?.cancel() }
    fun resumeRecording() { if (!_isRecording.value || !_isPaused.value) return; val pauseDuration = SystemClock.elapsedRealtime() - timePausedAt; recordingStartTime += pauseDuration; _isPaused.value = false; _weightAtPause.value = null; startTimer() }
    suspend fun stopRecordingAndSave(setupState: BrewSetupState): Long? {
        if (_countdown.value != null) { stopRecording(); return null }
        if (!_isRecording.value && !_isPaused.value) return null
        val finalTimeMillis = _recordingTimeMillis.value; val finalSamples = _recordedSamplesFlow.value; stopRecording()
        if (finalSamples.isEmpty()) { _error.value = "No data was recorded."; return null }
        val actualStartTimeMillis = System.currentTimeMillis() - finalTimeMillis; val beanId = setupState.selectedBean?.id; val doseGrams = setupState.doseGrams.toDoubleOrNull()
        if (beanId == null || doseGrams == null) { _error.value = "Missing bean or dose to save."; return null }
        val newBrew = Brew(beanId = beanId, doseGrams = doseGrams, startedAt = Date(actualStartTimeMillis), grinderId = setupState.selectedGrinder?.id, methodId = setupState.selectedMethod?.id, grindSetting = setupState.grindSetting.takeIf { it.isNotBlank() }, grindSpeedRpm = setupState.grindSpeedRpm.toDoubleOrNull(), brewTempCelsius = setupState.brewTempCelsius.toDoubleOrNull(), notes = "Recorded from scale ${Date()}")
        val result = viewModelScope.async { try { val newId = coffeeRepo.addBrewWithSamples(newBrew, finalSamples); _error.value = null; newId } catch (e: Exception) { _error.value = "Could not save brew: ${e.message}"; Log.e("ScaleViewModel", "Error saving brew: ${e.message}", e); null } }; return result.await()
    }
    fun stopRecording() { _countdown.value = null; _isRecording.value = false; _isPaused.value = false; _weightAtPause.value = null; timerJob?.cancel(); _recordedSamplesFlow.value = emptyList(); _recordingTimeMillis.value = 0L }
    private fun startTimer() { timerJob?.cancel(); timerJob = viewModelScope.launch { while (_isRecording.value && !_isPaused.value) { _recordingTimeMillis.value = SystemClock.elapsedRealtime() - recordingStartTime; delay(100) } } }
    private fun addSamplePoint(measurement: ScaleMeasurement) { if (!_isRecording.value || _isPaused.value) return; val elapsedTimeMs = SystemClock.elapsedRealtime() - recordingStartTime; val newSample = BrewSample(brewId = 0, timeMillis = elapsedTimeMs, massGrams = String.format(Locale.US, "%.1f", measurement.weightGrams).toDouble(), flowRateGramsPerSecond = String.format(Locale.US, "%.1f", measurement.flowRateGramsPerSecond).toDouble()); _recordedSamplesFlow.value = _recordedSamplesFlow.value + newSample }


    // --- FUNKTIONER FÖR REMEMBER PREFERENCE ---
    fun isRememberScaleEnabled(): Boolean {
        return sharedPreferences.getBoolean(PREF_REMEMBER_SCALE_ENABLED, false) // Default är false
    }

    fun setRememberScaleEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(PREF_REMEMBER_SCALE_ENABLED, enabled).apply()
        Log.d("ScaleViewModel", "Set remember scale enabled: $enabled")
        // Om användaren avmarkerar, glöm den sparade adressen
        if (!enabled) {
            saveRememberedScaleAddress(null)
        } else {
            // Om användaren markerar och vi är anslutna, spara den nuvarande adressen
            val currentState = connectionState.replayCache.lastOrNull()
            if (currentState is BleConnectionState.Connected) {
                saveRememberedScaleAddress(currentState.deviceName) // Använd deviceName som fallback om adress inte finns direkt
            }
        }
    }
    // --- SLUT FUNKTIONER FÖR REMEMBER PREFERENCE ---


    // --- ÄNDRAD: Kollar nu preferens först ---
    private fun saveRememberedScaleAddress(address: String?) {
        // Spara bara om "remember" är aktiverat
        if (isRememberScaleEnabled()) {
            sharedPreferences.edit().putString(PREF_REMEMBERED_SCALE_ADDRESS, address).apply()
            Log.d("ScaleViewModel", "Saved scale address: $address")
        } else {
            // Om "remember" inte är aktivt, se till att adressen är rensad
            sharedPreferences.edit().remove(PREF_REMEMBERED_SCALE_ADDRESS).apply()
            Log.d("ScaleViewModel", "Remember scale disabled, cleared saved address.")
        }
    }

    private fun loadRememberedScaleAddress(): String? {
        // Läs bara om "remember" är aktiverat
        return if (isRememberScaleEnabled()) {
            sharedPreferences.getString(PREF_REMEMBERED_SCALE_ADDRESS, null)
        } else {
            null
        }
    }

    // --- ÄNDRAD: Kollar nu preferens först ---
    private fun attemptAutoConnect() {
        // Försök bara om "remember" är aktiverat
        if (!isRememberScaleEnabled()) {
            Log.d("ScaleViewModel", "Remember scale is disabled, skipping auto-connect.")
            return
        }

        if (connectionState.replayCache.lastOrNull() is BleConnectionState.Connected) {
            Log.d("ScaleViewModel", "Already connected, skipping auto-connect.")
            return
        }
        val rememberedAddress = loadRememberedScaleAddress() // Denna returnerar null om remember är avstängt
        if (rememberedAddress != null) {
            Log.d("ScaleViewModel", "Attempting to auto-connect to: $rememberedAddress")
            isManualDisconnect = false // Säkerställ att flaggan är false för auto-connect
            val dummyDevice = DiscoveredDevice(name = null, address = rememberedAddress, rssi = 0)
            connect(dummyDevice)
        } else {
            Log.d("ScaleViewModel", "No remembered scale address found or remember is disabled.")
        }
    }

    // --- ÄNDRAD: Sparar adress endast om remember är aktivt ---
    private fun handleConnectionStateChange(state: BleConnectionState) {
        when (state) {
            is BleConnectionState.Connected -> {
                Log.d("ScaleViewModel", "Successfully connected to ${state.deviceName}.")
                // Spara adressen ENDAST om remember är aktiverat
                if (isRememberScaleEnabled()) {
                    saveRememberedScaleAddress(state.deviceName) // Använd deviceName som fallback
                }
                isManualDisconnect = false

                if (measurementJob?.isActive != true) {
                    measurementJob = viewModelScope.launch {
                        scaleRepo.observeMeasurements().collect { rawData ->
                            _rawMeasurement.value = rawData
                            if (_isRecording.value && !_isPaused.value) { addSamplePoint(measurement.value) }
                        }
                    }
                }
            }
            is BleConnectionState.Disconnected -> {
                Log.d("ScaleViewModel", "Disconnected. Manual disconnect flag: $isManualDisconnect")
                measurementJob?.cancel(); measurementJob = null

                // Försök återansluta ENDAST om det INTE var manuellt OCH remember är aktivt
                if (!isManualDisconnect && isRememberScaleEnabled()) {
                    viewModelScope.launch {
                        Log.d("ScaleViewModel", "Unexpected disconnect. Waiting 2 seconds before auto-reconnect attempt...")
                        delay(2000L)
                        if (connectionState.replayCache.lastOrNull() is BleConnectionState.Disconnected) {
                            Log.d("ScaleViewModel", "Still disconnected. Attempting auto-reconnect.")
                            attemptAutoConnect() // Denna kollar isRememberScaleEnabled igen
                        } else {
                            Log.d("ScaleViewModel", "State changed during delay, skipping auto-reconnect.")
                        }
                    }
                }
                isManualDisconnect = false
            }
            is BleConnectionState.Error -> {
                Log.e("ScaleViewModel", "Connection error: ${state.message}")
                measurementJob?.cancel(); measurementJob = null
                isManualDisconnect = false
            }
            is BleConnectionState.Connecting -> {
                Log.d("ScaleViewModel", "Connecting...")
            }
        }
    }
    // --- SLUT ÄNDRINGAR ---

    /**
     * NY FUNKTION: Nollställer felmeddelandet efter att det har visats.
     */
    fun clearError() {
        _error.value = null
    }
}