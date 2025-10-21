package com.victorkoffed.projektandroid.ui.viewmodel.scale

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.victorkoffed.projektandroid.data.repository.BookooScaleRepositoryImpl
import com.victorkoffed.projektandroid.data.repository.ScaleRepository
import com.victorkoffed.projektandroid.domain.model.BleConnectionState
import com.victorkoffed.projektandroid.domain.model.DiscoveredDevice
import com.victorkoffed.projektandroid.domain.model.ScaleMeasurement
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ScaleViewModel(app: Application) : AndroidViewModel(app) {

    private val repo: ScaleRepository = BookooScaleRepositoryImpl(app)

    // Scanning State
    private val _devices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val devices: StateFlow<List<DiscoveredDevice>> = _devices
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning
    private var scanJob: Job? = null

    // Connection and Measurement State
    val connectionState: StateFlow<BleConnectionState> = repo.observeConnectionState()
    private val _rawMeasurement = MutableStateFlow(ScaleMeasurement(0.0f))
    private val _tareOffset = MutableStateFlow(0.0f)

    /**
     * Ett kombinerat Flow som tar den råa mätningen från vågen,
     * subtraherar vårt mjukvaru-tare-offset och exponerar det rena värdet till UI:t.
     */
    val measurement: StateFlow<ScaleMeasurement> = combine(_rawMeasurement, _tareOffset) { raw, offset ->
        ScaleMeasurement(weightGrams = raw.weightGrams - offset)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ScaleMeasurement(0.0f)
    )

    // Error State
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun startScan() {
        if (_isScanning.value) return
        _devices.value = emptyList()
        _error.value = null
        _isScanning.value = true

        scanJob = viewModelScope.launch {
            repo.startScanDevices()
                .catch { e -> _error.value = e.message ?: "Okänt fel" }
                .onCompletion { _isScanning.value = false }
                .collect { list -> _devices.value = list }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        _isScanning.value = false
    }

    fun connect(device: DiscoveredDevice) {
        stopScan()
        // Återställ offset vid ny anslutning
        _tareOffset.value = 0.0f
        repo.connect(device.address)
        viewModelScope.launch {
            repo.observeMeasurements().collect {
                // Spara den råa, ofiltrerade datan
                _rawMeasurement.value = it
            }
        }
    }

    fun disconnect() {
        repo.disconnect()
        _tareOffset.value = 0.0f
    }

    /**
     * Skickar BÅDE ett hårdvarukommando och sätter en mjukvaru-offset.
     * Detta ger den mest stabila nollställningen.
     */
    fun tareScale() {
        // 1. Skicka det faktiska kommandot till vågen för att försöka nollställa den
        repo.tareScale()
        // 2. Sätt den nuvarande vikten som mjukvaru-offset för att garantera att UI:t visar 0.0
        _tareOffset.value = _rawMeasurement.value.weightGrams
    }
}

