package com.victorkoffed.projektandroid.ui.viewmodel.scale

import android.app.Application
import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.victorkoffed.projektandroid.data.db.BrewSample
import com.victorkoffed.projektandroid.data.repository.ScalePreferenceManager
import com.victorkoffed.projektandroid.data.repository.ScaleRepository
import com.victorkoffed.projektandroid.domain.model.BleConnectionState
import com.victorkoffed.projektandroid.domain.model.DiscoveredDevice
import com.victorkoffed.projektandroid.domain.model.ScaleMeasurement
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

// --- Konstanter ---
private const val TAG = "ScaleViewModel"
private val SCAN_TIMEOUT = 10.seconds

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
            rawMessage.contains("BLE scan failed") -> "BLE scan failed."
            rawMessage.contains("Bluetooth is turned off.") -> "Bluetooth is turned off."
            rawMessage.contains("Bluetooth hardware not available.") -> "Bluetooth hardware not available."
            rawMessage.contains("Bluetooth scanner unavailable.") -> "Bluetooth scanner unavailable."
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
    // private val coffeeRepo: CoffeeRepository, // Tas bort
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

    private val _isRecordingWhileDisconnected = MutableStateFlow(false)
    val isRecordingWhileDisconnected: StateFlow<Boolean> = _isRecordingWhileDisconnected.asStateFlow()

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
    val connectionState: StateFlow<BleConnectionState> = scaleRepo.observeConnectionState()
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
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = BleConnectionState.Disconnected // Sätter ett startvärde
        )


    init {
        // Observera råa mätvärden från repositoryt
        observeScaleMeasurements()
        // Försök anslut till ihågkommen våg om inställningen är på
        attemptAutoConnect()
    }

    private fun isBluetoothAvailableAndEnabled(): Boolean {
        val adapter = (getApplication<Application>().getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        return adapter != null && adapter.isEnabled
    }

    // --- Skanning & Anslutning ---

    fun startScan() {
        if (_isScanning.value) return
        _devices.value = emptyList(); clearError(); _isScanning.value = true; reconnectAttempted = false
        scanJob?.cancel(); scanTimeoutJob?.cancel()

        if (!isBluetoothAvailableAndEnabled()) {
            _error.value = "Bluetooth is turned off."
            _isScanning.value = false
            return
        }

        scanJob = viewModelScope.launch {
            try {
                scaleRepo.startScanDevices()
                    .catch { e ->
                        _error.value = BleErrorTranslator.translate(e.message)
                        _isScanning.value = false
                    }
                    .collect { _devices.value = it }
            } finally {
                if (_isScanning.value) _isScanning.value = false
            }
        }
        scanTimeoutJob = viewModelScope.launch {
            delay(SCAN_TIMEOUT.inWholeMilliseconds)
            if (_isScanning.value) stopScan()
        }
    }

    fun stopScan() {
        scanJob?.cancel(); scanTimeoutJob?.cancel()
        if (_isScanning.value) _isScanning.value = false
    }

    fun connect(device: DiscoveredDevice) {
        if (!isBluetoothAvailableAndEnabled()) {
            _error.value = "Bluetooth is turned off."
            return
        }

        stopScan()
        // ★★★ FIX: Nollställ bara tare-offset om vi INTE är mitt i en inspelning ★★★
        if (!_isRecording.value) {
            _tareOffset.value = 0.0f
        }
        isManualDisconnect = false
        reconnectAttempted = false
        clearError()
        scaleRepo.connect(device.address)
    }

    fun disconnect() {
        isManualDisconnect = true
        reconnectAttempted = false
        stopRecording()
        measurementJob?.cancel(); measurementJob = null
        scaleRepo.disconnect()
        _tareOffset.value = 0.0f
        Log.d(TAG, "Manual disconnect initiated.")
    }

    // --- Timer & Inspelning ---

    private fun observeScaleMeasurements() {
        if (measurementJob?.isActive == true) return
        measurementJob = viewModelScope.launch {
            scaleRepo.observeMeasurements()
                .catch { Log.e(TAG, "Error observing measurements", it) }
                .collect { rawData ->
                    _rawMeasurement.value = rawData
                    handleScaleTimer()
                    if (_isRecording.value && !_isPaused.value) {
                        addSamplePoint(measurement.value)
                    }
                }
        }
    }

    private fun handleScaleTimer() {
        if (_isRecording.value && !_isPaused.value && manualTimerJob == null) {
            startManualTimer()
        }
        else if (!_isRecording.value && !_isPaused.value && _recordingTimeMillis.value != 0L) {
            manualTimerJob?.cancel()
            manualTimerJob = null
            _recordingTimeMillis.value = 0L
        }
    }

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
            Log.d(TAG, "Manual timer loop stopped (paused or stopped).")
        }
    }

    fun tareScale() {
        if (connectionState.value !is BleConnectionState.Connected) {
            _error.value = "Scale not connected."; return
        }
        scaleRepo.tareScale()
        _tareOffset.value = _rawMeasurement.value.weightGrams
        if (_isRecording.value && !_isPaused.value) {
            addSamplePoint(measurement.value)
        }
    }

    fun startRecording() {
        if (_isRecording.value || _isPaused.value || _countdown.value != null) return
        if (connectionState.value !is BleConnectionState.Connected) { _error.value = "Scale not connected."; return }

        viewModelScope.launch {
            try {
                _countdown.value = 3; delay(1000L)
                _countdown.value = 2; delay(1000L)
                _countdown.value = 1; delay(1000L)

                // ★★★ FIX: Sätt offset FÖRST, baserat på aktuell råvikt ★★★
                // Detta synkroniserar appens "nollpunkt" med vågens.
                _tareOffset.value = _rawMeasurement.value.weightGrams

                scaleRepo.tareScaleAndStartTimer()
                delay(150L)
                _countdown.value = null
                internalStartRecording()
            } catch (e: Exception) {
                Log.e(TAG, "Error starting recording", e)
                _countdown.value = null; _error.value = "Could not start."
                if (connectionState.value is BleConnectionState.Connected) scaleRepo.resetTimer()
            }
        }
    }

    private fun internalStartRecording() {
        manualTimerJob?.cancel()
        manualTimerJob = null
        _recordedSamplesFlow.value = emptyList()
        _recordingTimeMillis.value = 0L
        _isPaused.value = false
        _isRecordingWhileDisconnected.value = false
        _weightAtPause.value = null

        _isRecording.value = true
        startManualTimer()
        Log.d(TAG, "Internal recording state started. Manual timer initiated.")
    }

    fun pauseRecording() {
        if (!_isRecording.value || _isPaused.value) return

        manualTimerJob?.cancel()
        manualTimerJob = null

        _isPaused.value = true
        _isRecordingWhileDisconnected.value = false
        _weightAtPause.value = measurement.value.weightGrams

        if (connectionState.value is BleConnectionState.Connected) {
            scaleRepo.stopTimer()
        }
        Log.d(TAG, "Manually paused. Manual timer stopped.")
    }

    fun resumeRecording() {
        if (!_isRecording.value || !_isPaused.value) return

        _isPaused.value = false
        _isRecordingWhileDisconnected.value = false
        _weightAtPause.value = null

        if (connectionState.value is BleConnectionState.Connected) {
            scaleRepo.startTimer()
            Log.d(TAG, "Resumed. Sent Start Timer (manual pause).")
        } else {
            Log.w(TAG, "Resumed but scale disconnected.")
            _isRecordingWhileDisconnected.value = true
        }

        startManualTimer()
    }

    fun stopRecording() {
        if (!_isRecording.value && !_isPaused.value && _countdown.value == null) return

        val wasRecordingOrPaused = _isRecording.value || _isPaused.value

        manualTimerJob?.cancel()
        manualTimerJob = null

        _countdown.value = null
        _isRecording.value = false
        _isPaused.value = false
        _isRecordingWhileDisconnected.value = false
        _weightAtPause.value = null
        _recordedSamplesFlow.value = emptyList()
        _recordingTimeMillis.value = 0L

        if (wasRecordingOrPaused && connectionState.value is BleConnectionState.Connected) {
            scaleRepo.resetTimer()
            Log.d(TAG, "Recording stopped/reset. Sent Reset Timer.")
        } else {
            Log.d(TAG, "Recording stopped/reset. (No Reset Timer sent)")
        }
    }

    private fun addSamplePoint(measurementData: ScaleMeasurement) {
        val sampleTimeMillis = _recordingTimeMillis.value
        if (sampleTimeMillis < 0) return

        val weightGramsDouble = String.format(Locale.US, "%.1f", measurementData.weightGrams).toDouble()
        val flowRateDouble = measurementData.formatFlowRateToDouble()

        val newSample = BrewSample(
            brewId = 0,
            timeMillis = sampleTimeMillis,
            massGrams = weightGramsDouble,
            flowRateGramsPerSecond = flowRateDouble
        )

        _recordedSamplesFlow.update { list ->
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

    private fun handleConnectionStateChange(state: BleConnectionState) {
        when (state) {
            is BleConnectionState.Connected -> handleConnectedState(state)
            is BleConnectionState.Disconnected, is BleConnectionState.Error -> handleDisconnectedOrErrorState(state)
            is BleConnectionState.Connecting -> { Log.d(TAG, "Connecting..."); clearError() }
        }
    }

    private fun handleConnectedState(state: BleConnectionState.Connected) {
        Log.i(TAG, "Connected to ${state.deviceName}.")
        reconnectAttempted = false
        if (rememberScaleEnabled.value) prefsManager.setRememberedScaleAddress(state.deviceAddress)
        isManualDisconnect = false
        clearError()
        observeScaleMeasurements()

        if (_isRecordingWhileDisconnected.value) {

            // ★★★ FIX: Justera tare-offset för att kompensera för vågens auto-tare ★★★
            // Vi vill att den nya vikten ska fortsätta från där den var.
            // UI = raw - offset
            // Vi vill att UI = _weightAtPause.value (t.ex. 200g)
            // Vågen har precis tarerat (antagande), så raw = 0.
            // 200.0 = 0.0 - offset
            // offset = -200.0
            _tareOffset.value = -(_weightAtPause.value ?: 0f)
            // ★★★ SLUT PÅ FIX ★★★

            _isRecordingWhileDisconnected.value = false
            Log.d(TAG, "Reconnected while recording. Data collection resumes. New Tare Offset: ${_tareOffset.value}")
        }
    }

    private fun handleDisconnectedOrErrorState(state: BleConnectionState) {
        val logMessage = when (state) {
            is BleConnectionState.Disconnected -> "Disconnected. Manual: $isManualDisconnect"
            is BleConnectionState.Error -> "Connection error: ${state.message}"
            else -> "Unexpected state handled: $state"
        }
        Log.d(TAG, logMessage)

        measurementJob?.cancel(); measurementJob = null

        if (!isManualDisconnect && _isRecording.value && !_isPaused.value) {
            _isRecordingWhileDisconnected.value = true
            _weightAtPause.value = measurement.value.weightGrams
            Log.w(TAG, "Recording... DISCONNECTED. Data collection paused, Timer continues.")
        } else if (!isManualDisconnect && _isRecording.value && _isPaused.value) {
            _isRecordingWhileDisconnected.value = true
            Log.w(TAG, "Manually Paused AND Disconnected.")
        } else if (!isManualDisconnect && _countdown.value != null) {
            stopRecording()
            Log.w(TAG, "Countdown cancelled due to disconnect/error.")
        } else if (!_isRecording.value) {
            manualTimerJob?.cancel(); manualTimerJob = null
        }

        if (reconnectAttempted) {
            Log.d(TAG, "Auto-reconnect attempt failed. Resetting lock.")
            reconnectAttempted = false
        }

        tryAutoReconnect()
    }

    private fun tryAutoReconnect() {
        val shouldAttempt = !isManualDisconnect && rememberScaleEnabled.value && autoConnectEnabled.value && !reconnectAttempted
        if (!shouldAttempt) return

        reconnectAttempted = true // Sätt låset

        viewModelScope.launch {
            delay(2000L)

            if (isManualDisconnect) {
                Log.d(TAG, "Auto-reconnect cancelled because isManualDisconnect is true after delay.")
                reconnectAttempted = false
                return@launch
            }

            val currentState = connectionState.value
            val stillNeedsReconnect = currentState !is BleConnectionState.Connected &&
                    currentState !is BleConnectionState.Connecting &&
                    rememberScaleEnabled.value && autoConnectEnabled.value

            if (stillNeedsReconnect) {
                Log.d(TAG, "Attempting auto-reconnect...")
                attemptAutoConnect()
            } else {
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
        if (enabled) {
            val cs = connectionState.value
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
        Log.d(TAG, "ForgetScale called. Stopping all BLE activity.")
        stopScan()
        disconnect()
        prefsManager.forgetScale()
        reconnectAttempted = false
    }

    private fun attemptAutoConnect() {
        if (!rememberScaleEnabled.value || !autoConnectEnabled.value) return
        val state = connectionState.value
        if (state is BleConnectionState.Connected || state is BleConnectionState.Connecting) {
            reconnectAttempted = false; return
        }

        val addr = prefsManager.loadRememberedScaleAddress()
        if (addr != null) {
            if (!isBluetoothAvailableAndEnabled()) {
                _error.value = "Bluetooth is turned off for auto-connect."
                reconnectAttempted = false
                return
            }

            isManualDisconnect = false
            clearError()
            scaleRepo.connect(addr)
        } else {
            reconnectAttempted = false
        }
    }

    fun retryConnection() {
        clearError()
        reconnectAttempted = false
        val addr = prefsManager.loadRememberedScaleAddress()
        if (addr != null) {
            if (!isBluetoothAvailableAndEnabled()) {
                _error.value = "Bluetooth is turned off. Please turn it on to reconnect."
                return
            }

            val state = connectionState.value
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
        if (connectionState.value !is BleConnectionState.Disconnected) {
            isManualDisconnect = true
            scaleRepo.disconnect()
        }
    }
}