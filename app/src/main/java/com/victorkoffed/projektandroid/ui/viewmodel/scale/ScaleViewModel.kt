package com.victorkoffed.projektandroid.ui.viewmodel.scale

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.util.Log
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle // <-- NY IMPORT
import androidx.lifecycle.viewModelScope
import com.victorkoffed.projektandroid.data.db.Brew
import com.victorkoffed.projektandroid.data.db.BrewSample
import com.victorkoffed.projektandroid.data.repository.CoffeeRepository
import com.victorkoffed.projektandroid.data.repository.ScaleRepository
import com.victorkoffed.projektandroid.domain.model.BleConnectionState
import com.victorkoffed.projektandroid.domain.model.DiscoveredDevice
import com.victorkoffed.projektandroid.domain.model.ScaleMeasurement
import com.victorkoffed.projektandroid.ui.viewmodel.brew.BrewSetupState
import dagger.hilt.android.lifecycle.HiltViewModel // <-- NY IMPORT
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
import javax.inject.Inject // <-- NY IMPORT
import kotlin.time.Duration.Companion.seconds

// Konstanter för SharedPreferences
private const val PREFS_NAME = "ScalePrefs"
private const val PREF_REMEMBERED_SCALE_ADDRESS = "remembered_scale_address"
private const val PREF_REMEMBER_SCALE_ENABLED = "remember_scale_enabled"

// NYTT: Dataklass för returvärdet från stopRecordingAndSave
data class SaveBrewResult(
    val brewId: Long?,
    val beanIdReachedZero: Long? = null // Bean ID om vikten blev <= 0
)

@HiltViewModel // <-- NY ANNOTERING
class ScaleViewModel @Inject constructor( // <-- NYTT: @Inject constructor
    app: Application, // Hilt tillhandahåller Application
    private val scaleRepo: ScaleRepository, // Injiceras av Hilt
    private val coffeeRepo: CoffeeRepository, // Injiceras av Hilt
    private val savedStateHandle: SavedStateHandle // Injiceras av Hilt (kan användas för att spara/återställa state)
) : AndroidViewModel(app) { // Ärver fortfarande från AndroidViewModel pga Application

    private val sharedPreferences: SharedPreferences = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private var isManualDisconnect = false

    // --- Scanning State ---
    private val _devices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val devices: StateFlow<List<DiscoveredDevice>> = _devices
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning
    private var scanJob: Job? = null

    // --- Connection and Measurement State ---
    val connectionState: SharedFlow<BleConnectionState> = scaleRepo.observeConnectionState()
        .onEach { state -> handleConnectionStateChange(state) }
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)

    private val _rawMeasurement = MutableStateFlow(ScaleMeasurement(0.0f, 0.0f))
    private val _tareOffset = MutableStateFlow(0.0f)
    val measurement: StateFlow<ScaleMeasurement> = combine(_rawMeasurement, _tareOffset) { raw, offset ->
        ScaleMeasurement(
            weightGrams = raw.weightGrams - offset,
            flowRateGramsPerSecond = raw.flowRateGramsPerSecond
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ScaleMeasurement(0.0f, 0.0f))

    // --- Error State ---
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // --- Recording State ---
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording
    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused
    private val _recordedSamplesFlow = MutableStateFlow<List<BrewSample>>(emptyList())
    val recordedSamplesFlow: StateFlow<List<BrewSample>> = _recordedSamplesFlow
    private val _recordingTimeMillis = MutableStateFlow(0L)
    val recordingTimeMillis: StateFlow<Long> = _recordingTimeMillis
    private val _weightAtPause = MutableStateFlow<Float?>(null)
    val weightAtPause: StateFlow<Float?> = _weightAtPause
    private val _countdown = MutableStateFlow<Int?>(null)
    val countdown: StateFlow<Int?> = _countdown

    private var recordingStartTime: Long = 0L
    private var timePausedAt: Long = 0L
    private var measurementJob: Job? = null
    private var timerJob: Job? = null
    // --- End Recording State ---

    init {
        // Försök återansluta vid start
        attemptAutoConnect()
    }

    // ---------------------------------------------------------------------------------------------
    // --- Funktionalitet för Skanning och Anslutning ---
    // ---------------------------------------------------------------------------------------------
    fun startScan() {
        if (_isScanning.value) return

        _devices.value = emptyList()
        _error.value = null
        _isScanning.value = true

        scanJob?.cancel()

        scanJob = viewModelScope.launch {
            try {
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
                if (_isScanning.value) {
                    _isScanning.value = false
                    Log.d("ScaleViewModel", "Skanning avslutad (timeout eller manuell).")
                }
            }
        }
    }

    fun stopScan() {
        if (scanJob?.isActive == true) {
            scanJob?.cancel()
            Log.d("ScaleViewModel", "Skanning stoppades manuellt av användaren.")
        }
    }

    fun connect(device: DiscoveredDevice) {
        stopScan()
        _tareOffset.value = 0.0f
        isManualDisconnect = false
        scaleRepo.connect(device.address)
    }

    fun disconnect() {
        Log.d("ScaleViewModel", "Manuell frånkoppling initierad.")
        isManualDisconnect = true
        stopRecording()
        measurementJob?.cancel(); measurementJob = null
        scaleRepo.disconnect()
        _tareOffset.value = 0.0f
    }

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
    fun startRecording() {
        if (_isRecording.value || _isPaused.value || _countdown.value != null) { Log.w("ScaleViewModel", "Start inspelning anropades men är redan upptagen."); return }
        if (connectionState.replayCache.lastOrNull() !is BleConnectionState.Connected) { _error.value = "Kan inte starta inspelning, vågen är inte ansluten."; return }
        viewModelScope.launch {
            try {
                Log.d("ScaleViewModel", "Initierar inspelningssekvens...")
                tareScale()
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

    // ÄNDRAD RETURTYP OCH LOGIK FÖR ATT RETURNERA SaveBrewResult
    suspend fun stopRecordingAndSave(setupState: BrewSetupState): SaveBrewResult {
        if (_countdown.value != null) { stopRecording(); return SaveBrewResult(null) } // Avbröt under nedräkning
        if (!_isRecording.value && !_isPaused.value) return SaveBrewResult(null) // Ingen inspelning pågick

        val finalTimeMillis = _recordingTimeMillis.value
        val finalSamples = _recordedSamplesFlow.value
        stopRecording() // Stoppa timer och återställ states

        if (finalSamples.isEmpty()) { _error.value = "Ingen data spelades in."; return SaveBrewResult(null) }

        val actualStartTimeMillis = System.currentTimeMillis() - finalTimeMillis
        val beanId = setupState.selectedBean?.id
        val doseGrams = setupState.doseGrams.toDoubleOrNull()

        if (beanId == null || doseGrams == null) { _error.value = "Saknar bönor eller dos för att spara."; return SaveBrewResult(null) }

        val newBrew = Brew(
            beanId = beanId,
            doseGrams = doseGrams,
            startedAt = Date(actualStartTimeMillis),
            grinderId = setupState.selectedGrinder?.id,
            methodId = setupState.selectedMethod?.id,
            grindSetting = setupState.grindSetting.takeIf { it.isNotBlank() },
            grindSpeedRpm = setupState.grindSpeedRpm.toDoubleOrNull(),
            brewTempCelsius = setupState.brewTempCelsius.toDoubleOrNull(),
            notes = "Inspelad från våg ${Date()}"
        )

        // Spara bryggning och hämta ID
        val savedBrewId: Long? = viewModelScope.async {
            try {
                // Denna metod minskar även lagersaldot
                val newId = coffeeRepo.addBrewWithSamples(newBrew, finalSamples)
                _error.value = null
                newId
            } catch (e: Exception) {
                _error.value = "Kunde inte spara bryggning: ${e.message}"
                Log.e("ScaleViewModel", "Fel vid sparning av bryggning: ${e.message}", e)
                null
            }
        }.await()

        var beanIdReachedZero: Long? = null
        if (savedBrewId != null) {
            // Kontrollera bönans kvarvarande vikt EFTER att bryggningen sparats
            // (addBrewWithSamples minskar vikten i en transaktion)
            try {
                val updatedBean = coffeeRepo.getBeanById(beanId)
                if (updatedBean != null && updatedBean.remainingWeightGrams <= 0.0 && !updatedBean.isArchived) {
                    // Om vikten är noll eller mindre OCH bönan inte redan är arkiverad
                    beanIdReachedZero = beanId
                    Log.d("ScaleViewModel", "Bean $beanId reached zero or less after saving brew $savedBrewId.")
                }
            } catch (e: Exception) {
                Log.e("ScaleViewModel", "Kunde inte hämta böna $beanId efter sparande för att kolla vikt.", e)
                // Fortsätt utan att skicka beanIdReachedZero
            }
        }

        // Returnera resultatet
        return SaveBrewResult(brewId = savedBrewId, beanIdReachedZero = beanIdReachedZero)
    }


    fun stopRecording() { _countdown.value = null; _isRecording.value = false; _isPaused.value = false; _weightAtPause.value = null; timerJob?.cancel(); _recordedSamplesFlow.value = emptyList(); _recordingTimeMillis.value = 0L }
    private fun startTimer() { timerJob?.cancel(); timerJob = viewModelScope.launch { while (_isRecording.value && !_isPaused.value) { _recordingTimeMillis.value = SystemClock.elapsedRealtime() - recordingStartTime; delay(100) } } }

    private fun addSamplePoint(measurement: ScaleMeasurement) { if (!_isRecording.value || _isPaused.value) return; val elapsedTimeMs = SystemClock.elapsedRealtime() - recordingStartTime; val newSample = BrewSample(brewId = 0, timeMillis = elapsedTimeMs, massGrams = String.format(Locale.US, "%.1f", measurement.weightGrams).toDouble(), flowRateGramsPerSecond = String.format(Locale.US, "%.1f", measurement.flowRateGramsPerSecond).toDouble()); _recordedSamplesFlow.value = _recordedSamplesFlow.value + newSample }

    // ---------------------------------------------------------------------------------------------
    // --- Funktionalitet för 'Kom Ihåg Våg' Preferens ---
    // ---------------------------------------------------------------------------------------------
    fun isRememberScaleEnabled(): Boolean {
        return sharedPreferences.getBoolean(PREF_REMEMBER_SCALE_ENABLED, false)
    }

    fun setRememberScaleEnabled(enabled: Boolean) {
        sharedPreferences.edit { putBoolean(PREF_REMEMBER_SCALE_ENABLED, enabled) }
        Log.d("ScaleViewModel", "Ställ in 'kom ihåg våg' till: $enabled")
        if (!enabled) {
            saveRememberedScaleAddress(null) // Ta bort sparad adress om funktionen inaktiveras
        } else {
            // Spara den nuvarande anslutna enhetens adress om funktionen aktiveras
            val currentState = connectionState.replayCache.lastOrNull()
            if (currentState is BleConnectionState.Connected) {
                saveRememberedScaleAddress(currentState.deviceName) // Antag att deviceName är adressen
            }
        }
    }

    private fun saveRememberedScaleAddress(address: String?) {
        if (address != null && isRememberScaleEnabled()) {
            sharedPreferences.edit { putString(PREF_REMEMBERED_SCALE_ADDRESS, address) }
            Log.d("ScaleViewModel", "Sparade vågadress: $address")
        } else {
            sharedPreferences.edit { remove(PREF_REMEMBERED_SCALE_ADDRESS) }
            Log.d("ScaleViewModel", "Rensade sparad vågadress.")
        }
    }

    private fun loadRememberedScaleAddress(): String? {
        return if (isRememberScaleEnabled()) {
            sharedPreferences.getString(PREF_REMEMBERED_SCALE_ADDRESS, null)
        } else {
            null
        }
    }

    private fun attemptAutoConnect() {
        if (!isRememberScaleEnabled()) {
            Log.d("ScaleViewModel", "'Kom ihåg våg' är inaktiverat, hoppar över auto-anslutning.")
            return
        }
        val lastState = connectionState.replayCache.lastOrNull()
        if (lastState is BleConnectionState.Connected || lastState is BleConnectionState.Connecting) {
            Log.d("ScaleViewModel", "Redan ansluten eller ansluter, hoppar över auto-anslutning.")
            return
        }

        val rememberedAddress = loadRememberedScaleAddress()
        if (rememberedAddress != null) {
            Log.d("ScaleViewModel", "Försöker auto-ansluta till: $rememberedAddress")
            isManualDisconnect = false
            scaleRepo.connect(rememberedAddress)
        } else {
            Log.d("ScaleViewModel", "Ingen sparad vågadress hittades för auto-anslutning.")
        }
    }

    // ---------------------------------------------------------------------------------------------
    // --- Hantering av Anslutningsstatus ---
    // ---------------------------------------------------------------------------------------------
    private fun handleConnectionStateChange(state: BleConnectionState) {
        when (state) {
            is BleConnectionState.Connected -> {
                Log.d("ScaleViewModel", "Ansluten till ${state.deviceName}.")
                // Spara adressen om "kom ihåg" är aktiverat
                if (isRememberScaleEnabled()) {
                    saveRememberedScaleAddress(state.deviceName) // Antag att deviceName är adressen
                }
                isManualDisconnect = false // Nollställ efter lyckad anslutning

                // Starta mätjobbet om det inte redan körs
                if (measurementJob?.isActive != true) {
                    measurementJob = viewModelScope.launch {
                        scaleRepo.observeMeasurements().collect { rawData ->
                            _rawMeasurement.value = rawData
                            // Lägg till sample point endast om inspelning pågår och inte är pausad
                            if (_isRecording.value && !_isPaused.value) { addSamplePoint(measurement.value) }
                        }
                    }
                }
            }
            is BleConnectionState.Disconnected -> {
                Log.d("ScaleViewModel", "Frånkopplad. Manuell frånkoppling-flagga: $isManualDisconnect")
                measurementJob?.cancel(); measurementJob = null

                // Stoppa inspelning/nedräkning vid oväntad frånkoppling
                if(!isManualDisconnect && (_isRecording.value || _isPaused.value || _countdown.value != null)) {
                    Log.w("ScaleViewModel", "Oväntad frånkoppling under inspelning/nedräkning. Stoppar.")
                    stopRecording() // Detta återställer alla inspelningsstates
                }

                // Försök återansluta automatiskt om det inte var manuellt och "kom ihåg" är på
                if (!isManualDisconnect && isRememberScaleEnabled()) {
                    viewModelScope.launch {
                        Log.d("ScaleViewModel", "Oväntad frånkoppling. Väntar 2 sekunder före försök till auto-återanslutning...")
                        delay(2000L)
                        // Dubbelkolla att vi fortfarande är frånkopplade (kan ha ändrats under delay)
                        if (connectionState.replayCache.lastOrNull() is BleConnectionState.Disconnected) {
                            Log.d("ScaleViewModel", "Fortfarande frånkopplad. Försöker auto-återanslutning.")
                            attemptAutoConnect()
                        } else {
                            Log.d("ScaleViewModel", "Status ändrades under fördröjningen, hoppar över auto-återanslutning.")
                        }
                    }
                }
                // Återställ flaggan EFTER att återanslutningslogiken har körts (eller om den inte kördes)
                isManualDisconnect = false
            }
            is BleConnectionState.Error -> {
                Log.e("ScaleViewModel", "Anslutningsfel: ${state.message}")
                measurementJob?.cancel(); measurementJob = null
                _error.value = "Anslutningsfel: ${state.message}"

                // Stoppa inspelning/nedräkning vid fel
                if(_isRecording.value || _isPaused.value || _countdown.value != null) {
                    Log.w("ScaleViewModel", "Anslutningsfel under inspelning/nedräkning. Stoppar.")
                    stopRecording()
                }
                isManualDisconnect = false // Nollställ flaggan även vid fel
            }
            is BleConnectionState.Connecting -> {
                Log.d("ScaleViewModel", "Ansluter...")
                clearError() // Rensa eventuella gamla fel när vi försöker ansluta igen
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}