// app/src/main/java/com/victorkoffed/projektandroid/ui/viewmodel/scale/ScaleViewModel.kt
package com.victorkoffed.projektandroid.ui.viewmodel.scale

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
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
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

private const val PREFS_NAME = "ScalePrefs"
private const val PREF_REMEMBERED_SCALE_ADDRESS = "remembered_scale_address"
private const val PREF_REMEMBER_SCALE_ENABLED = "remember_scale_enabled"
private const val PREF_AUTO_CONNECT_ENABLED = "auto_connect_enabled"

data class SaveBrewResult(val brewId: Long?, val beanIdReachedZero: Long? = null)

private object BleErrorTranslator {
    fun translate(rawMessage: String?): String {
        if (rawMessage == null) return "Ett okänt fel uppstod."
        // Simplified for brevity, use previous detailed version if preferred
        return when {
            rawMessage.contains("GATT Error (133)") -> "Anslutningen misslyckades (133)."
            rawMessage.contains("GATT Error") -> "Anslutningsfel ($rawMessage)."
            rawMessage.contains("permission", ignoreCase = true) -> "Bluetooth-behörighet saknas."
            rawMessage.contains("address", ignoreCase = true) -> "Ogiltig adress."
            rawMessage.contains("BLE scan failed") -> "Sökning misslyckades."
            rawMessage.contains("Bluetooth is not enabled") -> "Bluetooth avstängt."
            else -> rawMessage
        }
    }
}


@HiltViewModel
class ScaleViewModel @Inject constructor(
    app: Application,
    private val scaleRepo: ScaleRepository,
    private val coffeeRepo: CoffeeRepository
) : AndroidViewModel(app) {

    private val sharedPreferences: SharedPreferences = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var isManualDisconnect = false
    private var reconnectAttempted = false

    // --- States ---
    private val _devices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val devices: StateFlow<List<DiscoveredDevice>> = _devices
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning
    private var scanJob: Job? = null
    private var scanTimeoutJob: Job? = null
    val connectionState: SharedFlow<BleConnectionState> = scaleRepo.observeConnectionState()
        .map { state -> if (state is BleConnectionState.Error) BleConnectionState.Error(BleErrorTranslator.translate(state.message)) else state }
        .onEach { state -> handleConnectionStateChange(state) }
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)
    private val _rawMeasurement = MutableStateFlow(ScaleMeasurement(0.0f, 0.0f))
    private val _tareOffset = MutableStateFlow(0.0f)
    val measurement: StateFlow<ScaleMeasurement> = combine(_rawMeasurement, _tareOffset) { raw, offset ->
        ScaleMeasurement(weightGrams = raw.weightGrams - offset, flowRateGramsPerSecond = raw.flowRateGramsPerSecond, timeMillis = raw.timeMillis)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ScaleMeasurement(0.0f, 0.0f))
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
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
    private val _rememberScaleEnabled = MutableStateFlow(sharedPreferences.getBoolean(PREF_REMEMBER_SCALE_ENABLED, false))
    val rememberScaleEnabled: StateFlow<Boolean> = _rememberScaleEnabled.asStateFlow()
    private val _autoConnectEnabled = MutableStateFlow(sharedPreferences.getBoolean(PREF_AUTO_CONNECT_ENABLED, _rememberScaleEnabled.value))
    val autoConnectEnabled: StateFlow<Boolean> = _autoConnectEnabled.asStateFlow()
    private val _rememberedScaleAddress = MutableStateFlow(loadRememberedScaleAddress())
    val rememberedScaleAddress: StateFlow<String?> = _rememberedScaleAddress.asStateFlow()
    private var measurementJob: Job? = null

    init {
        observeScaleMeasurements()
        attemptAutoConnect()
    }

    private fun observeScaleMeasurements() {
        if (measurementJob?.isActive == true) return
        measurementJob = viewModelScope.launch {
            scaleRepo.observeMeasurements()
                .catch { Log.e("ScaleViewModel", "Error observing measurements", it) }
                .collect { rawData ->
                    _rawMeasurement.value = rawData
                    // Update timer display based on scale data if recording/paused
                    if ((_isRecording.value || _isPaused.value) && rawData.timeMillis != null) {
                        if (_recordingTimeMillis.value != rawData.timeMillis) {
                            _recordingTimeMillis.value = rawData.timeMillis
                        }
                    } else if (!_isRecording.value && !_isPaused.value && _recordingTimeMillis.value != 0L) {
                        _recordingTimeMillis.value = 0L // Reset display time if stopped
                    }
                    // Add sample point if recording and not paused
                    if (_isRecording.value && !_isPaused.value) {
                        addSamplePoint(measurement.value)
                    }
                }
        }
    }

    // --- Skanning & Anslutning ---
    fun startScan() {
        if (_isScanning.value) return
        _devices.value = emptyList(); clearError(); _isScanning.value = true; reconnectAttempted = false
        scanJob?.cancel(); scanTimeoutJob?.cancel()
        scanJob = viewModelScope.launch { try { scaleRepo.startScanDevices().catch { e -> _error.value = BleErrorTranslator.translate(e.message); _isScanning.value = false }.collect { _devices.value = it } } finally { if (_isScanning.value) _isScanning.value = false } }
        scanTimeoutJob = viewModelScope.launch { delay(10.seconds); if (_isScanning.value) stopScan() }
    }
    fun stopScan() { scanJob?.cancel(); scanTimeoutJob?.cancel(); if (_isScanning.value) _isScanning.value = false }
    fun connect(device: DiscoveredDevice) { stopScan(); _tareOffset.value = 0.0f; isManualDisconnect = false; reconnectAttempted = false; clearError(); scaleRepo.connect(device.address) }
    fun disconnect() { isManualDisconnect = true; reconnectAttempted = false; stopRecording(); measurementJob?.cancel(); measurementJob = null; scaleRepo.disconnect(); _tareOffset.value = 0.0f }
    fun tareScale() { scaleRepo.tareScale(); _tareOffset.value = _rawMeasurement.value.weightGrams; if (_isRecording.value && !_isPaused.value) addSamplePoint(measurement.value) }

    // --- Inspelning ---
    fun startRecording() {
        if (_isRecording.value || _isPaused.value || _countdown.value != null) return
        if (connectionState.replayCache.lastOrNull() !is BleConnectionState.Connected) { _error.value = "Scale not connected."; return }

        viewModelScope.launch {
            try {
                Log.d("ScaleViewModel", "Initiating recording sequence...")
                _countdown.value = 3; delay(1000L)
                _countdown.value = 2; delay(1000L)
                _countdown.value = 1; delay(1000L)

                // Send tare/start AFTER countdown
                scaleRepo.tareScaleAndStartTimer()
                Log.d("ScaleViewModel", "Sent Tare and Start Timer command.")
                delay(150L) // Brief pause for scale

                _countdown.value = null
                internalStartRecording() // Start app's recording state

            } catch (e: Exception) {
                Log.e("ScaleViewModel", "Error starting recording", e);
                _countdown.value = null; _error.value = "Could not start."
                // Reset timer on scale if start failed?
                if (connectionState.replayCache.lastOrNull() is BleConnectionState.Connected) scaleRepo.resetTimer()
            }
        }
    }

    private fun internalStartRecording() {
        _recordedSamplesFlow.value = emptyList(); _recordingTimeMillis.value = 0L; _isPaused.value = false; _weightAtPause.value = null; _isRecording.value = true;
        addSamplePoint(measurement.value.copy(timeMillis = 0L)) // Add initial point
        Log.d("ScaleViewModel", "Internal recording state started.")
    }

    fun pauseRecording() {
        if (!_isRecording.value || _isPaused.value) return
        _isPaused.value = true
        _weightAtPause.value = measurement.value.weightGrams
        // Send Stop Timer command
        if (connectionState.replayCache.lastOrNull() is BleConnectionState.Connected) {
            scaleRepo.stopTimer()
        }
        Log.d("ScaleViewModel", "Paused. Sent Stop Timer.")
    }

    fun resumeRecording() {
        if (!_isRecording.value || !_isPaused.value) return
        _isPaused.value = false
        _weightAtPause.value = null
        // Send Start Timer command
        if (connectionState.replayCache.lastOrNull() is BleConnectionState.Connected) {
            scaleRepo.startTimer()
        }
        Log.d("ScaleViewModel", "Resumed. Sent Start Timer.")
    }

    suspend fun stopRecordingAndSave(setupState: BrewSetupState): SaveBrewResult {
        if (_countdown.value != null || (!_isRecording.value && !_isPaused.value)) { stopRecording(); return SaveBrewResult(null) }
        val finalTimeMillis = _recordingTimeMillis.value; val finalSamples = _recordedSamplesFlow.value;
        stopRecording() // This now sends Reset Timer

        if (finalSamples.size < 2 || finalTimeMillis <= 0) { _error.value = "Not enough data."; return SaveBrewResult(null) }

        val actualStartTimeMillis = System.currentTimeMillis() - finalTimeMillis; val beanId = setupState.selectedBean?.id; val doseGrams = setupState.doseGrams.toDoubleOrNull()
        if (beanId == null || doseGrams == null) { _error.value = "Missing bean/dose."; return SaveBrewResult(null) }
        val scaleInfo = (connectionState.replayCache.lastOrNull() as? BleConnectionState.Connected)?.let { " via ${it.deviceName}" } ?: ""
        val newBrew = Brew(beanId = beanId, doseGrams = doseGrams, startedAt = Date(actualStartTimeMillis), grinderId = setupState.selectedGrinder?.id, methodId = setupState.selectedMethod?.id, grindSetting = setupState.grindSetting.takeIf { it.isNotBlank() }, grindSpeedRpm = setupState.grindSpeedRpm.toDoubleOrNull(), brewTempCelsius = setupState.brewTempCelsius.toDoubleOrNull(), notes = "Recorded${scaleInfo} on ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())}")

        val savedBrewId: Long? = viewModelScope.async { try { val id = coffeeRepo.addBrewWithSamples(newBrew, finalSamples); clearError(); id } catch (e: Exception) { _error.value = "Save failed: ${e.message}"; null } }.await()
        var beanIdReachedZero: Long? = null; if (savedBrewId != null) { try { val bean = coffeeRepo.getBeanById(beanId); if (bean != null && bean.remainingWeightGrams <= 0.0 && !bean.isArchived) beanIdReachedZero = beanId } catch (e: Exception) { Log.e("ScaleVM", "Check weight failed", e) } }
        return SaveBrewResult(brewId = savedBrewId, beanIdReachedZero = beanIdReachedZero)
    }

    fun stopRecording() { // Also used for Reset button
        if (!_isRecording.value && !_isPaused.value && _countdown.value == null) return

        val wasActive = _isRecording.value || _isPaused.value

        _countdown.value = null
        _isRecording.value = false
        _isPaused.value = false
        _weightAtPause.value = null
        _recordedSamplesFlow.value = emptyList()
        _recordingTimeMillis.value = 0L

        // Send Reset Timer command if recording was active and connected
        if (wasActive && connectionState.replayCache.lastOrNull() is BleConnectionState.Connected) {
            scaleRepo.resetTimer()
            Log.d("ScaleViewModel", "Recording stopped/reset. Sent Reset Timer.")
        } else {
            Log.d("ScaleViewModel", "Recording stopped/reset. (No Reset Timer sent)")
        }
    }

    private fun addSamplePoint(measurement: ScaleMeasurement) {
        val elapsedTimeMs = measurement.timeMillis ?: _recordingTimeMillis.value; if (elapsedTimeMs < 0) return
        val newSample = BrewSample(brewId = 0, timeMillis = elapsedTimeMs, massGrams = String.format(Locale.US, "%.1f", measurement.weightGrams).toDouble(), flowRateGramsPerSecond = String.format(Locale.US, "%.1f", measurement.flowRateGramsPerSecond).toDouble())
        // Avoid adding points with the exact same timestamp multiple times
        _recordedSamplesFlow.update { list -> if (list.lastOrNull()?.timeMillis == newSample.timeMillis && list.isNotEmpty()) list else list + newSample }
    }


    // --- Kom ihåg/Auto-connect ---
    fun setRememberScaleEnabled(enabled: Boolean) {
        sharedPreferences.edit { putBoolean(PREF_REMEMBER_SCALE_ENABLED, enabled) }
        _rememberScaleEnabled.value = enabled
        if (!enabled) { setAutoConnectEnabled(false); saveRememberedScaleAddress(null) }
        else { val cs = connectionState.replayCache.lastOrNull(); if (cs is BleConnectionState.Connected) { saveRememberedScaleAddress(cs.deviceAddress); setAutoConnectEnabled(true) } else setAutoConnectEnabled(false) }
    }
    fun setAutoConnectEnabled(enabled: Boolean) {
        val newValue = enabled && _rememberScaleEnabled.value; if (_autoConnectEnabled.value != newValue) { sharedPreferences.edit { putBoolean(PREF_AUTO_CONNECT_ENABLED, newValue) }; _autoConnectEnabled.value = newValue }
    }
    private fun saveRememberedScaleAddress(address: String?) {
        val current = _rememberedScaleAddress.value; if (address != null && _rememberScaleEnabled.value) { if (current != address) { sharedPreferences.edit { putString(PREF_REMEMBERED_SCALE_ADDRESS, address) }; _rememberedScaleAddress.value = address } }
        else { if (current != null) { sharedPreferences.edit { remove(PREF_REMEMBERED_SCALE_ADDRESS) }; _rememberedScaleAddress.value = null; setAutoConnectEnabled(false) } }
    }
    private fun loadRememberedScaleAddress(): String? = if (_rememberScaleEnabled.value) sharedPreferences.getString(PREF_REMEMBERED_SCALE_ADDRESS, null) else null
    fun forgetRememberedScale() { setRememberScaleEnabled(false) }
    private fun attemptAutoConnect() {
        if (!_rememberScaleEnabled.value || !_autoConnectEnabled.value) return; val state = connectionState.replayCache.lastOrNull(); if (state is BleConnectionState.Connected || state is BleConnectionState.Connecting) { reconnectAttempted = false; return }
        val addr = loadRememberedScaleAddress(); if (addr != null) { isManualDisconnect = false; clearError(); scaleRepo.connect(addr) }
    }

    // --- Connection State Change Handler ---
    private fun handleConnectionStateChange(state: BleConnectionState) {
        when (state) {
            is BleConnectionState.Connected -> {
                Log.i("ScaleViewModel", "Connected to ${state.deviceName}.")
                reconnectAttempted = false; if (_rememberScaleEnabled.value) saveRememberedScaleAddress(state.deviceAddress); isManualDisconnect = false; clearError()
                observeScaleMeasurements() // Start/Restart observation
            }
            is BleConnectionState.Disconnected -> {
                Log.d("ScaleViewModel", "Disconnected. Manual: $isManualDisconnect")
                measurementJob?.cancel(); measurementJob = null // Stop observation
                if(!isManualDisconnect && (_isRecording.value || _isPaused.value || _countdown.value != null)) { stopRecording() } // Reset recording state
                // Auto-reconnect logic
                val shouldRetry = !isManualDisconnect && _rememberScaleEnabled.value && _autoConnectEnabled.value && !reconnectAttempted
                if (shouldRetry) { viewModelScope.launch { delay(2000L); val cs = connectionState.replayCache.lastOrNull(); val stillNeeds = cs is BleConnectionState.Disconnected && _rememberScaleEnabled.value && _autoConnectEnabled.value && !reconnectAttempted; if (stillNeeds) { reconnectAttempted = true; attemptAutoConnect() } } }
            }
            is BleConnectionState.Error -> {
                Log.e("ScaleViewModel", "Connection error: ${state.message}")
                measurementJob?.cancel(); measurementJob = null // Stop observation
                if(_isRecording.value || _isPaused.value || _countdown.value != null) { stopRecording() } // Reset recording state
                // Auto-reconnect logic
                val shouldRetry = !isManualDisconnect && _rememberScaleEnabled.value && _autoConnectEnabled.value && !reconnectAttempted
                if (shouldRetry) { viewModelScope.launch { delay(2000L); val cs = connectionState.replayCache.lastOrNull(); val stillNeeds = cs !is BleConnectionState.Connected && cs !is BleConnectionState.Connecting && _rememberScaleEnabled.value && _autoConnectEnabled.value && !reconnectAttempted; if (stillNeeds) { reconnectAttempted = true; attemptAutoConnect() } } }
            }
            is BleConnectionState.Connecting -> { Log.d("ScaleViewModel", "Connecting..."); clearError() }
        }
    }

    // --- Övriga funktioner ---
    fun retryConnection() {
        clearError(); reconnectAttempted = false; val addr = loadRememberedScaleAddress(); if (addr != null) { val state = connectionState.replayCache.lastOrNull(); if (state is BleConnectionState.Connected || state is BleConnectionState.Connecting) return; isManualDisconnect = false; scaleRepo.connect(addr) } else { _error.value = "No scale remembered." }
    }
    fun clearError() { if (_error.value != null) _error.value = null }
    override fun onCleared() {
        super.onCleared(); Log.d("ScaleViewModel", "onCleared."); stopScan(); measurementJob?.cancel(); scanTimeoutJob?.cancel(); if (connectionState.replayCache.lastOrNull() !is BleConnectionState.Disconnected) { isManualDisconnect = true; disconnect() }
    }
}