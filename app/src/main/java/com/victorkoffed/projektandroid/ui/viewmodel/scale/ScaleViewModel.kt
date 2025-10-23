package com.victorkoffed.projektandroid.ui.viewmodel.scale

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.victorkoffed.projektandroid.data.db.Brew
import com.victorkoffed.projektandroid.data.db.BrewSample
import com.victorkoffed.projektandroid.data.repository.CoffeeRepository
import com.victorkoffed.projektandroid.data.repository.ScaleRepository
import com.victorkoffed.projektandroid.domain.model.BleConnectionState
import com.victorkoffed.projektandroid.domain.model.DiscoveredDevice
import com.victorkoffed.projektandroid.domain.model.ScaleMeasurement
import com.victorkoffed.projektandroid.ui.viewmodel.brew.BrewSetupState // <-- Korrekt import
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Date
import java.util.Locale

class ScaleViewModel(
    app: Application,
    private val scaleRepo: ScaleRepository,
    private val coffeeRepo: CoffeeRepository
) : AndroidViewModel(app) {

    // --- Scanning State ---
    private val _devices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val devices: StateFlow<List<DiscoveredDevice>> = _devices
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning
    private var scanJob: Job? = null

    // --- Connection and Measurement State ---
    val connectionState: StateFlow<BleConnectionState> = scaleRepo.observeConnectionState()
    private val _rawMeasurement = MutableStateFlow(ScaleMeasurement(0.0f))
    private val _tareOffset = MutableStateFlow(0.0f)

    val measurement: StateFlow<ScaleMeasurement> = combine(_rawMeasurement, _tareOffset) { raw, offset ->
        ScaleMeasurement(weightGrams = raw.weightGrams - offset)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ScaleMeasurement(0.0f)
    )

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

    private var recordingStartTime: Long = 0L
    private var timePausedAt: Long = 0L
    private var measurementJob: Job? = null
    private var timerJob: Job? = null
    // --- End Recording State ---

    // --- Vanliga funktioner ---
    fun startScan() {
        if (_isScanning.value) return
        _devices.value = emptyList(); _error.value = null; _isScanning.value = true
        scanJob = viewModelScope.launch {
            scaleRepo.startScanDevices()
                .catch { e -> _error.value = e.message ?: "Okänt fel" }
                .onCompletion { _isScanning.value = false }
                .collect { list -> _devices.value = list }
        }
    }
    fun stopScan() { scanJob?.cancel(); scanJob = null; _isScanning.value = false }

    fun connect(device: DiscoveredDevice) {
        stopScan()
        _tareOffset.value = 0.0f
        scaleRepo.connect(device.address)
        measurementJob?.cancel()
        measurementJob = viewModelScope.launch {
            scaleRepo.observeMeasurements().collect { rawData ->
                _rawMeasurement.value = rawData
                if (_isRecording.value && !_isPaused.value) {
                    addSamplePoint(measurement.value.weightGrams.toDouble())
                }
            }
        }
    }

    fun disconnect() {
        stopRecording()
        measurementJob?.cancel(); measurementJob = null
        scaleRepo.disconnect()
        _tareOffset.value = 0.0f
    }

    fun tareScale() {
        scaleRepo.tareScale()
        _tareOffset.value = _rawMeasurement.value.weightGrams
        if (_isRecording.value && !_isPaused.value) {
            addSamplePoint(0.0)
        }
    }
    // --- Slut på vanliga funktioner ---

    // --- Recording Functions ---
    fun startRecording() {
        if (_isRecording.value || connectionState.value !is BleConnectionState.Connected) return
        _recordedSamplesFlow.value = emptyList()
        _recordingTimeMillis.value = 0L
        _isPaused.value = false
        _weightAtPause.value = null
        recordingStartTime = SystemClock.elapsedRealtime()
        _isRecording.value = true
        addSamplePoint(measurement.value.weightGrams.toDouble())
        startTimer()
    }

    fun pauseRecording() {
        if (!_isRecording.value || _isPaused.value) return
        _isPaused.value = true
        timePausedAt = SystemClock.elapsedRealtime()
        _weightAtPause.value = measurement.value.weightGrams
        timerJob?.cancel()
    }

    fun resumeRecording() {
        if (!_isRecording.value || !_isPaused.value) return
        val pauseDuration = SystemClock.elapsedRealtime() - timePausedAt
        recordingStartTime += pauseDuration
        _isPaused.value = false
        _weightAtPause.value = null
        startTimer()
    }


    /**
     * Stoppar inspelningen och sparar Brew + Samples.
     * Tar nu emot hela BrewSetupState för att spara alla detaljer.
     * @return id för nyskapad Brew, eller null om inget sparades / fel inträffade.
     */
    suspend fun stopRecordingAndSave(
        setupState: BrewSetupState // Tar emot hela setupen
    ): Long? {
        if (!_isRecording.value && !_isPaused.value) return null
        _isRecording.value = false
        _isPaused.value = false
        _weightAtPause.value = null
        timerJob?.cancel()

        if (_recordedSamplesFlow.value.isEmpty()) {
            _recordingTimeMillis.value = 0L
            return null
        }

        val actualStartTimeMillis = System.currentTimeMillis() - (SystemClock.elapsedRealtime() - recordingStartTime)

        // Hämta nödvändig data från setupState
        val beanId = setupState.selectedBean?.id
        val doseGrams = setupState.doseGrams.toDoubleOrNull()

        if (beanId == null || doseGrams == null) {
            _error.value = "Saknar böna eller dos för att spara."
            _recordedSamplesFlow.value = emptyList() // Rensa ändå?
            _recordingTimeMillis.value = 0L
            return null
        }

        // KORRIGERING: Skapa Brew-objektet med ALL data från setupState
        val newBrew = Brew(
            beanId = beanId,
            doseGrams = doseGrams,
            startedAt = Date(actualStartTimeMillis),
            // Hämta resten från setupState, konvertera vid behov
            grinderId = setupState.selectedGrinder?.id, // Använd grinderId från setup
            methodId = setupState.selectedMethod?.id, // Använd methodId från setup
            grindSetting = setupState.grindSetting.takeIf { it.isNotBlank() }, // Använd grindSetting från setup
            grindSpeedRpm = setupState.grindSpeedRpm.toDoubleOrNull(), // Använd RPM från setup
            brewTempCelsius = setupState.brewTempCelsius.toDoubleOrNull(), // Använd Temp från setup
            notes = "Inspelad från våg ${Date()}" // TODO: Hämta noter från setup?
        )

        val result = viewModelScope.async {
            try {
                val newId = coffeeRepo.addBrewWithSamples(newBrew, _recordedSamplesFlow.value)
                _recordedSamplesFlow.value = emptyList()
                _error.value = null
                _recordingTimeMillis.value = 0L
                newId
            } catch (e: Exception) {
                _error.value = "Kunde inte spara bryggning: ${e.message}"
                _recordedSamplesFlow.value = emptyList()
                _recordingTimeMillis.value = 0L
                null
            }
        }
        return result.await()
    }

    // --- Helper functions ---
    private fun stopRecording() {
        _isRecording.value = false
        _isPaused.value = false
        _weightAtPause.value = null
        timerJob?.cancel()
        _recordedSamplesFlow.value = emptyList()
        _recordingTimeMillis.value = 0L
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_isRecording.value && !_isPaused.value) {
                _recordingTimeMillis.value = SystemClock.elapsedRealtime() - recordingStartTime
                delay(100)
            }
        }
    }

    private fun addSamplePoint(massGrams: Double) {
        if (_isPaused.value) return

        val elapsedTimeMs = SystemClock.elapsedRealtime() - recordingStartTime
        val newSample = BrewSample(
            brewId = 0, // sätts korrekt av repository när Brew har skapats
            timeMillis = elapsedTimeMs,
            massGrams = String.format(Locale.US, "%.1f", massGrams).toDouble()
        )
        _recordedSamplesFlow.value = _recordedSamplesFlow.value + newSample
    }
    // --- End Recording Functions ---
}

