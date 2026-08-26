/*
 * Reference Note (AI Assistance): The management of auto-reconnect logic and
 * BleErrorTranslator was developed with AI assistance to create a robust
 * state machine. See README.md for AI tools.
 */

package com.victorkoffed.projektandroid.ui.viewmodel.scale

import android.app.Application
import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.victorkoffed.projektandroid.data.repository.ScalePreferenceManager
import com.victorkoffed.projektandroid.data.repository.interfaces.ScaleRepository
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers

private const val TAG = "ScaleViewModel"
private val SCAN_TIMEOUT = 10.seconds

/**
 * Translates low-level Bluetooth Low Energy error states into user-facing messages.
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
 * ViewModel managing Bluetooth Low Energy peripheral discovery, pairing connections,
 * and persistent auto-reconnection state machines.
 */
@HiltViewModel
class ScaleViewModel @Inject constructor(
    app: Application,
    private val scaleRepo: ScaleRepository,
    private val prefsManager: ScalePreferenceManager
) : AndroidViewModel(app) {

    private var isManualDisconnect = false
    private var reconnectAttempted = false

    private val _devices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val devices: StateFlow<List<DiscoveredDevice>> = _devices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val rememberScaleEnabled: StateFlow<Boolean> = prefsManager.rememberScaleEnabled
    val autoConnectEnabled: StateFlow<Boolean> = prefsManager.autoConnectEnabled
    val rememberedScaleAddress: StateFlow<String?> = prefsManager.rememberedScaleAddress

    private var scanJob: Job? = null
    private var scanTimeoutJob: Job? = null

    val measurement: StateFlow<ScaleMeasurement> = scaleRepo.observeMeasurements()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ScaleMeasurement(0.0f, 0.0f))

    val connectionState: StateFlow<BleConnectionState> = scaleRepo.observeConnectionState()
        .map { state ->
            if (state is BleConnectionState.Error) {
                BleConnectionState.Error(BleErrorTranslator.translate(state.message))
            } else {
                state
            }
        }
        .onEach { state -> handleConnectionStateChange(state) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = BleConnectionState.Disconnected
        )


    init {
        viewModelScope.launch {
            delay(1500L)

            val state = connectionState.value
            if (state is BleConnectionState.Disconnected && !isManualDisconnect) {
                Log.d(TAG, "Init: Fördröjd auto-anslutning startar.")
                attemptAutoConnect()
            } else {
                Log.d(TAG, "Init: Skippar fördröjd auto-anslutning (State: $state, ManualDisconnect: $isManualDisconnect)")
            }
        }
    }

    private fun isBluetoothAvailableAndEnabled(): Boolean {
        val adapter = (getApplication<Application>().getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        return adapter != null && adapter.isEnabled
    }

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
        isManualDisconnect = false
        reconnectAttempted = false
        clearError()
        viewModelScope.launch(Dispatchers.IO) {
            scaleRepo.connect(device.address)
        }
    }

    fun disconnect() {
        isManualDisconnect = true
        reconnectAttempted = false
        scaleRepo.disconnect()
        Log.d(TAG, "Manual disconnect initiated.")
    }

    fun tareScale() {
        if (connectionState.value !is BleConnectionState.Connected) {
            _error.value = "Scale not connected."; return
        }
        scaleRepo.tareScale()
    }

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
    }

    private fun handleDisconnectedOrErrorState(state: BleConnectionState) {
        val logMessage = when (state) {
            is BleConnectionState.Disconnected -> "Disconnected. Manual: $isManualDisconnect"
            is BleConnectionState.Error -> "Connection error: ${state.message}"
            else -> "Unexpected state handled: $state"
        }
        Log.d(TAG, logMessage)

        if (reconnectAttempted) {
            Log.d(TAG, "Auto-reconnect attempt failed. Resetting lock.")
            reconnectAttempted = false
        }

        tryAutoReconnect()
    }

    private fun tryAutoReconnect() {
        val shouldAttempt = !isManualDisconnect && rememberScaleEnabled.value && autoConnectEnabled.value && !reconnectAttempted
        if (!shouldAttempt) return

        reconnectAttempted = true

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
            viewModelScope.launch(Dispatchers.IO) {
                scaleRepo.connect(addr)
            }
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
            viewModelScope.launch(Dispatchers.IO) {
                scaleRepo.connect(addr)
            }
        } else {
            _error.value = "No scale remembered."
        }
    }

    fun clearError() {
        if (_error.value != null) _error.value = null
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "onCleared. Cleaning up resources.")
        stopScan()
        scanTimeoutJob?.cancel()
        if (connectionState.value !is BleConnectionState.Disconnected) {
            isManualDisconnect = true
            scaleRepo.disconnect()
        }
    }
}