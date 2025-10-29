package com.victorkoffed.projektandroid.ui.viewmodel.scale

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.victorkoffed.projektandroid.data.db.Brew
import com.victorkoffed.projektandroid.data.db.BrewSample
import com.victorkoffed.projektandroid.data.repository.CoffeeRepository
import com.victorkoffed.projektandroid.data.repository.ScalePreferenceManager
import com.victorkoffed.projektandroid.data.repository.ScaleRepository
import com.victorkoffed.projektandroid.domain.model.BleConnectionState
import com.victorkoffed.projektandroid.domain.model.DiscoveredDevice
import com.victorkoffed.projektandroid.domain.model.ScaleMeasurement
import com.victorkoffed.projektandroid.ui.viewmodel.brew.BrewSetupState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

// --- Konstanter ---
private const val TAG = "ScaleViewModel"
private val SCAN_TIMEOUT = 10.seconds

/** Data class som returneras efter att en bryggning har sparats. */
data class SaveBrewResult(val brewId: Long?, val beanIdReachedZero: Long? = null)

/**
 * Översätter råa BLE-felmeddelanden till användarvänliga strängar.
 */
private object BleErrorTranslator {
    fun translate(rawMessage: String?): String {
        if (rawMessage == null) return "An unknown error occurred."
        return when {
            rawMessage.contains("GATT Error (133)") -> "Connection failed (133)."
            rawMessage.contains("GATT Error") -> "Connection error ($rawMessage)."
            rawMessage.contains("permission", ignoreCase = true) -> "Bluetooth permission missing."
            rawMessage.contains("address", ignoreCase = true) -> "Invalid address."
            rawMessage.contains("BLE scan failed") -> "Scan failed."
            rawMessage.contains("Bluetooth off") -> "Bluetooth turned off."
            else -> rawMessage
        }
    }
}

/**
 * ViewModel som hanterar all logik relaterad till BLE-vågen:
 * Skanning, anslutning, inspelning av mätdata, timerhantering och auto-connect.
 */
@HiltViewModel
class ScaleViewModel @Inject constructor(
    app: Application,
    private val scaleRepo: ScaleRepository,
    private val coffeeRepo: CoffeeRepository,
    private val prefsManager: ScalePreferenceManager
) : AndroidViewModel(app) {

    private var isManualDisconnect = false
    private var reconnectAttempted = false

    // --- StateFlows (Exposed to UI) ---
    private val _devices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val devices: StateFlow<List<DiscoveredDevice>> = _devices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _isPausedDueToDisconnect = MutableStateFlow(false)
    val isPausedDueToDisconnect: StateFlow<Boolean> = _isPausedDueToDisconnect.asStateFlow()

    private val _recordedSamplesFlow = MutableStateFlow<List<BrewSample>>(emptyList())
    val recordedSamplesFlow: StateFlow<List<BrewSample>> = _recordedSamplesFlow.asStateFlow()

    private val _recordingTimeMillis = MutableStateFlow(0L)
    val recordingTimeMillis: StateFlow<Long> = _recordingTimeMillis.asStateFlow()

    private val _weightAtPause = MutableStateFlow<Float?>(null)
    val weightAtPause: StateFlow<Float?> = _weightAtPause.asStateFlow()

    private val _countdown = MutableStateFlow<Int?>(null)
    val countdown: StateFlow<Int?> = _countdown.asStateFlow()

    // Hämta inställningar från Preference Manager
    val rememberScaleEnabled: StateFlow<Boolean> = prefsManager.rememberScaleEnabled
    val autoConnectEnabled: StateFlow<Boolean> = prefsManager.autoConnectEnabled
    val rememberedScaleAddress: StateFlow<String?> = prefsManager.rememberedScaleAddress
    // REMOVED old preference flows

    // --- Private Job Management ---
    private var scanJob: Job? = null
    private var scanTimeoutJob: Job? = null
    private var measurementJob: Job? = null
    private var manualTimerJob: Job? = null // Används för att simulera tid vid disconnect

    // --- Mätdata (interna/bearbetade) ---
    private val _rawMeasurement = MutableStateFlow(ScaleMeasurement(0.0f, 0.0f))
    private val _tareOffset = MutableStateFlow(0.0f)

    /** Kombinerat Flöde för aktuell vikt och flöde (justerat för tarering). */
    val measurement: StateFlow<ScaleMeasurement> = combine(_rawMeasurement, _tareOffset) { raw, offset ->
        ScaleMeasurement(
            weightGrams = raw.weightGrams - offset,
            flowRateGramsPerSecond = raw.flowRateGramsPerSecond,
            timeMillis = raw.timeMillis
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ScaleMeasurement(0.0f, 0.0f))

    /** Delat Flöde för anslutningsstatus (med översatta fel och sidoeffekter). */
    val connectionState: SharedFlow<BleConnectionState> = scaleRepo.observeConnectionState()
        .map { state ->
            // Översätt råa felmeddelanden innan de exponeras
            if (state is BleConnectionState.Error) {
                BleConnectionState.Error(BleErrorTranslator.translate(state.message))
            } else {
                state
            }
        }
        // Hantera sidoeffekter (auto-connect, paus vid disconnect) i denna onEach
        .onEach { state -> handleConnectionStateChange(state) }
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)


    init {
        // Observera råa mätvärden från repositoryt
        observeScaleMeasurements()
        // Försök anslut till ihågkommen våg om inställningen är på
        attemptAutoConnect()
    }

    // --- Skanning & Anslutning ---

    fun startScan() {
        if (_isScanning.value) return
        _devices.value = emptyList(); clearError(); _isScanning.value = true; reconnectAttempted = false
        scanJob?.cancel(); scanTimeoutJob?.cancel()

        scanJob = viewModelScope.launch {
            try {
                scaleRepo.startScanDevices()
                    .catch { e ->
                        // Fånga fel som tillstånd saknas/Bluetooth avstängt
                        _error.value = BleErrorTranslator.translate(e.message)
                        _isScanning.value = false
                    }
                    .collect { _devices.value = it }
            } finally {
                // Säkerställ att scanning flaggan stängs av även om flowet avslutas
                if (_isScanning.value) _isScanning.value = false
            }
        }
        // Tidsbegränsa scanningen
        scanTimeoutJob = viewModelScope.launch {
            delay(SCAN_TIMEOUT)
            if (_isScanning.value) stopScan()
        }
    }

    fun stopScan() {
        scanJob?.cancel(); scanTimeoutJob?.cancel()
        if (_isScanning.value) _isScanning.value = false
    }

    fun connect(device: DiscoveredDevice) {
        stopScan()
        _tareOffset.value = 0.0f
        isManualDisconnect = false
        reconnectAttempted = false // Nollställ här vid manuell anslutning
        clearError()
        scaleRepo.connect(device.address)
    }

    fun disconnect() {
        isManualDisconnect = true // Markera som manuell (förhindrar auto-reconnect)
        reconnectAttempted = false // Nollställ här vid manuell frånkoppling
        stopRecording() // Stoppa inspelningen
        // Avbryt mätjobbet och nollställ anslutning/offset
        measurementJob?.cancel(); measurementJob = null
        scaleRepo.disconnect()
        _tareOffset.value = 0.0f
        Log.d(TAG, "Manual disconnect initiated.")
    }

    // --- Timer & Inspelning ---

    /** Hanterar rådata från vågen och uppdaterar tids-/sampel-flöden. */
    private fun observeScaleMeasurements() {
        if (measurementJob?.isActive == true) return
        measurementJob = viewModelScope.launch {
            scaleRepo.observeMeasurements()
                .catch { Log.e(TAG, "Error observing measurements", it) }
                .collect { rawData ->
                    _rawMeasurement.value = rawData

                    // Hantera tid baserat på vågens interna timer
                    handleScaleTimer(rawData.timeMillis)

                    // Lägg till mätpunkt om inspelning pågår och INTE är pausad (oavsett orsak)
                    if (_isRecording.value && !_isPaused.value) {
                        addSamplePoint(measurement.value)
                    }
                }
        }
    }

    /** Synkroniserar appens tid med vågens interna timer, eller simulerar den. */
    private fun handleScaleTimer(scaleTimeMillis: Long?) {
        if (_isRecording.value && !_isPaused.value) {
            // Om vågen rapporterar tid och manuell timer inte körs
            if (scaleTimeMillis != null) {
                // Använd vågens tid, avbryt manuell simulering
                manualTimerJob?.cancel()
                manualTimerJob = null
                if (_recordingTimeMillis.value != scaleTimeMillis) {
                    _recordingTimeMillis.value = scaleTimeMillis
                }
            } else if (manualTimerJob == null) {
                // Om vi inte har vågtid, starta manuell simulering (får aldrig hända vid Connect)
                startManualTimer()
            }
        } else if (!_isRecording.value && !_isPaused.value && _recordingTimeMillis.value != 0L) {
            // Nollställ tid om inspelningen är helt avslutad
            if (manualTimerJob == null) {
                _recordingTimeMillis.value = 0L
            }
        }
    }

    /** Startar en coroutine som simulerar tiden (används vid återupptagning utan vågtid). */
    private fun startManualTimer() {
        manualTimerJob?.cancel()
        manualTimerJob = viewModelScope.launch {
            Log.d(TAG, "Starting manual timer.")
            while (isActive && _isRecording.value && !_isPaused.value) {
                delay(100L)
                if (isActive && _isRecording.value && !_isPaused.value) {
                    _recordingTimeMillis.update { it + 100L }
                }
            }
        }
    }

    fun tareScale() {
        if (connectionState.replayCache.lastOrNull() !is BleConnectionState.Connected) {
            _error.value = "Scale not connected."; return
        }
        scaleRepo.tareScale()
        // Använd intern tare offset för att omedelbart visa noll i UI
        _tareOffset.value = _rawMeasurement.value.weightGrams

        // Lägg till sample point vid tarering om inspelning/paus pågår
        if (_isRecording.value && !_isPaused.value) {
            addSamplePoint(measurement.value)
        }
    }

    /** Startar nedräkning och signalerar till vågen att starta timer. */
    fun startRecording() {
        if (_isRecording.value || _isPaused.value || _countdown.value != null) return
        if (connectionState.replayCache.lastOrNull() !is BleConnectionState.Connected) { _error.value = "Scale not connected."; return }

        viewModelScope.launch {
            try {
                // Nedräkning
                _countdown.value = 3; delay(1000L)
                _countdown.value = 2; delay(1000L)
                _countdown.value = 1; delay(1000L)

                // Skicka kommando till vågen
                scaleRepo.tareScaleAndStartTimer()
                delay(150L) // Kort paus för vågen att svara

                _countdown.value = null
                internalStartRecording() // Starta appens inspelningsläge

            } catch (e: Exception) {
                Log.e(TAG, "Error starting recording", e)
                _countdown.value = null; _error.value = "Could not start."
                if (connectionState.replayCache.lastOrNull() is BleConnectionState.Connected) scaleRepo.resetTimer()
            }
        }
    }

    private fun internalStartRecording() {
        manualTimerJob?.cancel()
        manualTimerJob = null
        _recordedSamplesFlow.value = emptyList()
        _recordingTimeMillis.value = 0L
        _isPaused.value = false
        _isPausedDueToDisconnect.value = false
        _weightAtPause.value = null
        _isRecording.value = true
        Log.d(TAG, "Internal recording state started.")
    }

    /** Manuell paus av inspelningen. */
    fun pauseRecording() {
        if (!_isRecording.value || _isPaused.value) return
        manualTimerJob?.cancel() // Avbryt manuell timer
        _isPaused.value = true
        _isPausedDueToDisconnect.value = false
        _weightAtPause.value = measurement.value.weightGrams
        if (connectionState.replayCache.lastOrNull() is BleConnectionState.Connected) {
            scaleRepo.stopTimer()
        }
        Log.d(TAG, "Manually paused.")
    }

    /** Återuppta inspelningen. */
    fun resumeRecording() {
        if (!_isRecording.value || !_isPaused.value) return
        _isPaused.value = false
        _isPausedDueToDisconnect.value = false
        _weightAtPause.value = null

        // Skicka Start Timer-kommando endast om ansluten
        if (connectionState.replayCache.lastOrNull() is BleConnectionState.Connected) {
            scaleRepo.startTimer()
            Log.d(TAG, "Resumed. Sent Start Timer.")
        } else {
            // Om vågen inte är ansluten, starta simulering av tid
            startManualTimer()
            Log.w(TAG, "Resumed but scale disconnected. Starting manual timer.")
        }
    }

    /** Stoppar inspelningen, sparar data och rensar state. */
    suspend fun stopRecordingAndSave(setupState: BrewSetupState): SaveBrewResult {
        if (_countdown.value != null || (!_isRecording.value && !_isPaused.value)) {
            stopRecording(); return SaveBrewResult(null)
        }
        val finalTimeMillis = _recordingTimeMillis.value; val finalSamples = _recordedSamplesFlow.value
        stopRecording()

        if (finalSamples.size < 2 || finalTimeMillis <= 0) {
            _error.value = "Not enough data recorded to save."; return SaveBrewResult(null)
        }

        // Validera Setup
        val beanId = setupState.selectedBean?.id; val doseGrams = setupState.doseGrams.toDoubleOrNull()
        if (beanId == null || doseGrams == null) {
            _error.value = "Missing bean/dose in setup."; return SaveBrewResult(null)
        }

        // Skapa Brew-objekt
        val actualStartTimeMillis = System.currentTimeMillis() - finalTimeMillis
        val scaleInfo = (connectionState.replayCache.lastOrNull() as? BleConnectionState.Connected)?.let { " via ${it.deviceName}" } ?: ""
        val newBrew = Brew(
            beanId = beanId, doseGrams = doseGrams, startedAt = Date(actualStartTimeMillis), grinderId = setupState.selectedGrinder?.id,
            methodId = setupState.selectedMethod?.id, grindSetting = setupState.grindSetting.takeIf { it.isNotBlank() },
            grindSpeedRpm = setupState.grindSpeedRpm.toDoubleOrNull(), brewTempCelsius = setupState.brewTempCelsius.toDoubleOrNull(),
            notes = "Recorded${scaleInfo} on ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())}"
        )

        // Spara Brew och Samples (med transaktion)
        val savedBrewId: Long? = viewModelScope.async {
            try {
                val id = coffeeRepo.addBrewWithSamples(newBrew, finalSamples)
                clearError(); id
            } catch (e: Exception) {
                _error.value = "Save failed: ${e.message}"; null
            }
        }.await()

        var beanIdReachedZero: Long? = null
        if (savedBrewId != null) {
            // Kontrollera om lagersaldot nu är noll efter att dosen dragits bort
            try {
                val bean = coffeeRepo.getBeanById(beanId)
                if (bean != null && bean.remainingWeightGrams <= 0.0 && !bean.isArchived) {
                    beanIdReachedZero = beanId
                }
            } catch (e: Exception) {
                Log.e(TAG, "Check bean stock failed after save", e)
            }
        }
        return SaveBrewResult(brewId = savedBrewId, beanIdReachedZero = beanIdReachedZero)
    }

    /** Stoppar inspelning/paus, nollställer state och skickar Reset Timer. */
    fun stopRecording() {
        if (!_isRecording.value && !_isPaused.value && _countdown.value == null) return

        val wasRecordingOrPaused = _isRecording.value || _isPaused.value
        manualTimerJob?.cancel()
        manualTimerJob = null

        _countdown.value = null
        _isRecording.value = false
        _isPaused.value = false
        _isPausedDueToDisconnect.value = false
        _weightAtPause.value = null
        _recordedSamplesFlow.value = emptyList()
        _recordingTimeMillis.value = 0L

        // Skicka Reset Timer-kommando om inspelning var aktiv/pausad och ansluten
        if (wasRecordingOrPaused && connectionState.replayCache.lastOrNull() is BleConnectionState.Connected) {
            scaleRepo.resetTimer()
            Log.d(TAG, "Recording stopped/reset. Sent Reset Timer.")
        } else {
            Log.d(TAG, "Recording stopped/reset. (No Reset Timer sent)")
        }
    }

    /** Lägger till en datapunkt till inspelningslistan. */
    private fun addSamplePoint(measurementData: ScaleMeasurement) {
        val sampleTimeMillis = _recordingTimeMillis.value
        if (sampleTimeMillis < 0) return

        // Formatera vikten och flödet till 1 decimal för att undvika onödig precision
        val weightGramsDouble = String.format(Locale.US, "%.1f", measurementData.weightGrams).toDouble()
        val flowRateDouble = measurementData.formatFlowRateToDouble()

        val newSample = BrewSample(
            brewId = 0,
            timeMillis = sampleTimeMillis,
            massGrams = weightGramsDouble,
            flowRateGramsPerSecond = flowRateDouble
        )

        _recordedSamplesFlow.update { list ->
            // Undvik dubbletter vid samma tidpunkt (kan hända vid 0 ms)
            if (list.lastOrNull()?.timeMillis == newSample.timeMillis && list.isNotEmpty()) {
                list
            } else {
                list + newSample
            }
        }
    }

    private fun ScaleMeasurement.formatFlowRateToDouble(): Double {
        return flowRateGramsPerSecond.let { flow ->
            String.format(Locale.US, "%.1f", flow).toDouble()
        }
    }

    // --- Hantera anslutningsstatus (Auto-Connect & Disconnect) ---

    /** Hanterar sidoeffekter vid ändringar i anslutningsstatus. */
    private fun handleConnectionStateChange(state: BleConnectionState) {
        when (state) {
            is BleConnectionState.Connected -> handleConnectedState(state)
            is BleConnectionState.Disconnected, is BleConnectionState.Error -> handleDisconnectedOrErrorState(state)
            is BleConnectionState.Connecting -> { Log.d(TAG, "Connecting..."); clearError() }
        }
    }

    private fun handleConnectedState(state: BleConnectionState.Connected) {
        Log.i(TAG, "Connected to ${state.deviceName}.")
        reconnectAttempted = false // Sätt till false vid lyckad anslutning
        // ANVÄND MANAGER: Uppdatera sparad adress om 'remember' är aktivt
        if (rememberScaleEnabled.value) prefsManager.setRememberedScaleAddress(state.deviceAddress)
        isManualDisconnect = false
        clearError()
        observeScaleMeasurements() // Säkerställ att mätningar observeras

        // Om vi var pausade pga disconnect, återställ flaggan.
        if (_isPausedDueToDisconnect.value) {
            _isPausedDueToDisconnect.value = false
            Log.d(TAG, "Reconnected while paused. Ready for manual resume.")
            // OBS: Inspelningen är fortfarande _isPaused = true, användaren måste trycka på Resume.
        }
    }

    private fun handleDisconnectedOrErrorState(state: BleConnectionState) {
        // Logik för att sätta logMessage
        val logMessage = when (state) {
            is BleConnectionState.Disconnected -> {
                "Disconnected. Manual: $isManualDisconnect"
            }
            is BleConnectionState.Error -> {
                "Connection error: ${state.message}" // Nu säkerställt att state har .message
            }
            else -> {
                "Unexpected state handled: $state" // Fallback (bör inte hända här)
            }
        }
        Log.d(TAG, logMessage)

        measurementJob?.cancel(); measurementJob = null // Stoppa observation av mätvärden
        manualTimerJob?.cancel(); manualTimerJob = null // Stoppa manuell timer

        // Logik för att pausa pågående inspelning vid oväntad frånkoppling
        if (!isManualDisconnect && _isRecording.value && !_isPaused.value) {
            _isPaused.value = true
            _isPausedDueToDisconnect.value = true
            _weightAtPause.value = measurement.value.weightGrams
            Log.w(TAG, "Recording paused due to unexpected disconnect/error.")
        } else if (!isManualDisconnect && _countdown.value != null) {
            // Om nedräkning pågick, avbryt den (stopRecording hanterar detta)
            stopRecording()
            Log.w(TAG, "Countdown cancelled due to disconnect/error.")
        }

        // Auto-återanslutningslogik
        tryAutoReconnect()
    }

    private fun tryAutoReconnect() {
        val shouldAttempt = !isManualDisconnect && rememberScaleEnabled.value && autoConnectEnabled.value && !reconnectAttempted
        if (!shouldAttempt) return

        // FIX: Sätt flaggan OMEDELBART för att blockera nya försök under de 2 sekunderna.
        reconnectAttempted = true

        viewModelScope.launch {
            delay(2000L) // Vänta lite innan återförsök
            val currentState = connectionState.replayCache.lastOrNull()

            // Kontrollera om återanslutning fortfarande behövs
            val stillNeedsReconnect = currentState !is BleConnectionState.Connected &&
                    currentState !is BleConnectionState.Connecting &&
                    rememberScaleEnabled.value && autoConnectEnabled.value

            if (stillNeedsReconnect) {
                Log.d(TAG, "Attempting auto-reconnect...")
                // Återanslutningsförsök startas. Flaggan är redan satt som lås.
                attemptAutoConnect()
            } else {
                // FIX: Om anslutningen INTE startades här och vi inte lyckades ansluta, nollställ låset.
                // (Om anslutningen lyckades rensas flaggan i handleConnectedState).
                if (currentState !is BleConnectionState.Connected) {
                    reconnectAttempted = false
                }
            }
        }
    }

    // --- Inställningar (Persistence) ---

    fun setRememberScaleEnabled(enabled: Boolean) {
        prefsManager.setRememberScaleEnabled(enabled)
        reconnectAttempted = false

        // Måste även hantera sparande av adress här om vi har en anslutning (för att hantera omedelbart aktivering)
        if (enabled) {
            val cs = connectionState.replayCache.lastOrNull()
            if (cs is BleConnectionState.Connected) {
                prefsManager.setRememberedScaleAddress(cs.deviceAddress)
                prefsManager.setAutoConnectEnabled(true)
            }
        }
    }

    fun setAutoConnectEnabled(enabled: Boolean) {
        prefsManager.setAutoConnectEnabled(enabled)
    }

    fun forgetRememberedScale() {
        prefsManager.forgetScale()
        // Nollställ lås
        reconnectAttempted = false
    }

    private fun attemptAutoConnect() {
        // Använder Manager för att läsa tillstånd
        if (!rememberScaleEnabled.value || !autoConnectEnabled.value) return
        val state = connectionState.replayCache.lastOrNull()
        if (state is BleConnectionState.Connected || state is BleConnectionState.Connecting) {
            reconnectAttempted = false; return
        }

        // Använder Manager för att hämta adressen
        val addr = prefsManager.loadRememberedScaleAddress()
        if (addr != null) {
            isManualDisconnect = false
            clearError()
            scaleRepo.connect(addr)
        }
    }

    fun retryConnection() {
        clearError()
        reconnectAttempted = false
        val addr = prefsManager.loadRememberedScaleAddress()
        if (addr != null) {
            val state = connectionState.replayCache.lastOrNull()
            if (state is BleConnectionState.Connected || state is BleConnectionState.Connecting) return
            isManualDisconnect = false
            scaleRepo.connect(addr)
        } else {
            _error.value = "No scale remembered."
        }
    }

    // --- Övriga funktioner ---
    fun clearError() {
        if (_error.value != null) _error.value = null
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "onCleared. Cleaning up resources.")
        stopScan()
        measurementJob?.cancel()
        scanTimeoutJob?.cancel()
        manualTimerJob?.cancel()
        // Stäng anslutningen om den var aktiv och inte redan Disconnected
        if (connectionState.replayCache.lastOrNull() !is BleConnectionState.Disconnected) {
            isManualDisconnect = true // Markera som avsiktlig disconnect
            scaleRepo.disconnect()
        }
    }
}