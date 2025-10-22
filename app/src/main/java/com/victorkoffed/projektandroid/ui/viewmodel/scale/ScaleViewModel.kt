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
import kotlinx.coroutines.Job
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

    // Exponerar den tarerade vikten
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

    private val _isPaused = MutableStateFlow(false) // <-- NYTT STATE FÖR PAUS
    val isPaused: StateFlow<Boolean> = _isPaused

    private val _recordedSamplesFlow = MutableStateFlow<List<BrewSample>>(emptyList())
    val recordedSamplesFlow: StateFlow<List<BrewSample>> = _recordedSamplesFlow

    private val _recordingTimeMillis = MutableStateFlow(0L)
    val recordingTimeMillis: StateFlow<Long> = _recordingTimeMillis

    private var recordingStartTime: Long = 0L
    private var timePausedAt: Long = 0L // För att spara tiden när paus inträffade
    private var measurementJob: Job? = null
    private var timerJob: Job? = null
    // --- End Recording State ---

    // --- Vanliga funktioner (oförändrade) ---
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
                // Lägg bara till sample om vi spelar in OCH inte är pausade
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
        // Lägg till nollpunkt om vi spelar in (och inte är pausade)
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
        _isPaused.value = false // Se till att vi inte är pausade vid start
        recordingStartTime = SystemClock.elapsedRealtime()
        _isRecording.value = true
        addSamplePoint(measurement.value.weightGrams.toDouble()) // Startpunkt
        startTimer() // Starta timern
    }

    fun pauseRecording() { // <-- NY FUNKTION
        if (!_isRecording.value || _isPaused.value) return
        _isPaused.value = true
        timePausedAt = SystemClock.elapsedRealtime() // Spara tidpunkten för pausen
        timerJob?.cancel() // Stoppa timern
    }

    fun resumeRecording() { // <-- NY FUNKTION
        if (!_isRecording.value || !_isPaused.value) return
        // Justera starttiden för att kompensera för paustiden
        val pauseDuration = SystemClock.elapsedRealtime() - timePausedAt
        recordingStartTime += pauseDuration
        _isPaused.value = false
        startTimer() // Starta timern igen
    }


    fun stopRecordingAndSave(beanIdToUse: Long, doseGramsToUse: Double) {
        // Stoppa inspelning oavsett om pausad eller ej
        if (!_isRecording.value && !_isPaused.value) return // Går bara att stoppa om inspelning startats
        _isRecording.value = false
        _isPaused.value = false
        timerJob?.cancel()

        // Spara bara om vi faktiskt har samples
        if (_recordedSamplesFlow.value.isEmpty()) {
            _recordingTimeMillis.value = 0L
            return
        }

        // Skapa Brew-objekt
        val actualStartTimeMillis = System.currentTimeMillis() - (SystemClock.elapsedRealtime() - recordingStartTime) // Beräkna starttid
        val newBrew = Brew(
            beanId = beanIdToUse,
            doseGrams = doseGramsToUse,
            startedAt = Date(actualStartTimeMillis),
            grinderId = null, methodId = null, grindSetting = null,
            grindSpeedRpm = null, brewTempCelsius = null,
            notes = "Inspelad från våg ${Date()}"
        )

        // Spara Brew och Samples
        viewModelScope.launch {
            try {
                coffeeRepo.addBrewWithSamples(newBrew, _recordedSamplesFlow.value)
                _recordedSamplesFlow.value = emptyList() // Rensa efter lyckad sparande
                _error.value = null
                _recordingTimeMillis.value = 0L
                // TODO: Navigera bort eller visa success
            } catch (e: Exception) {
                _error.value = "Kunde inte spara bryggning: ${e.message}"
                // Behåll samples för ev. nytt försök? Kanske inte. Rensa ändå.
                _recordedSamplesFlow.value = emptyList()
                _recordingTimeMillis.value = 0L
            }
        }
    }

    // Helper to stop recording without saving (anropas vid t.ex. disconnect)
    private fun stopRecording() {
        _isRecording.value = false
        _isPaused.value = false
        timerJob?.cancel()
        _recordedSamplesFlow.value = emptyList()
        _recordingTimeMillis.value = 0L
    }

    // Helper function to start the timer coroutine
    private fun startTimer() {
        timerJob?.cancel() // Säkerställ att bara en timer körs
        timerJob = viewModelScope.launch {
            // Fortsätt så länge vi spelar in OCH inte är pausade
            while (_isRecording.value && !_isPaused.value) {
                _recordingTimeMillis.value = SystemClock.elapsedRealtime() - recordingStartTime
                delay(100) // Uppdatera tiden
            }
        }
    }

    // Helper function to add a sample point
    private fun addSamplePoint(massGrams: Double) {
        // Lägg bara till punkten om vi inte är pausade
        if (!_isPaused.value) {
            val elapsedTimeMs = SystemClock.elapsedRealtime() - recordingStartTime
            val newSample = BrewSample(
                brewId = 0,
                timeMillis = elapsedTimeMs,
                // Avrunda till en decimal för att minska datamängd?
                massGrams = String.format(Locale.US, "%.1f", massGrams).toDouble()
            )
            _recordedSamplesFlow.value = _recordedSamplesFlow.value + newSample
        }
    }
    // --- End Recording Functions ---
}

