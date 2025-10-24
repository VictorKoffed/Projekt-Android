package com.victorkoffed.projektandroid.ui.viewmodel.scale

// --- KONTROLLERA ATT ALLA DESSA IMPORTER FINNS ---
import android.app.Application
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

class ScaleViewModel : AndroidViewModel {

    private val scaleRepo: ScaleRepository
    private val coffeeRepo: CoffeeRepository

    constructor(app: Application, scaleRepo: ScaleRepository, coffeeRepo: CoffeeRepository) : super(
        app
    ) {
        this.scaleRepo = scaleRepo
        this.coffeeRepo = coffeeRepo
        this._devices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
        this.devices = _devices
        this._isScanning = MutableStateFlow(false)
        this.isScanning = _isScanning
        this.connectionState = scaleRepo.observeConnectionState()
        this._rawMeasurement = MutableStateFlow(ScaleMeasurement(0.0f, 0.0f))
        this._tareOffset = MutableStateFlow(0.0f)
        this.measurement = combine(_rawMeasurement, _tareOffset) { raw, offset ->
            ScaleMeasurement(
                weightGrams = raw.weightGrams - offset,
                flowRateGramsPerSecond = raw.flowRateGramsPerSecond // Flödet påverkas inte av tarering
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
    }

    // --- Scanning State ---
    private val _devices: MutableStateFlow<List<DiscoveredDevice>>
    val devices: StateFlow<List<DiscoveredDevice>>
    private val _isScanning: MutableStateFlow<Boolean>
    val isScanning: StateFlow<Boolean>
    private var scanJob: Job? = null

    // --- Connection and Measurement State ---
    val connectionState: StateFlow<BleConnectionState>
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
        scaleRepo.connect(device.address)
        measurementJob?.cancel()
        measurementJob = viewModelScope.launch {
            scaleRepo.observeMeasurements().collect { rawData ->
                _rawMeasurement.value = rawData
                if (_isRecording.value && !_isPaused.value) {
                    addSamplePoint(measurement.value) // Skicka hela objektet
                }
            }
        }
    }

    fun disconnect() {
        stopRecording() // Stoppa ev. inspelning vid frånkoppling
        measurementJob?.cancel(); measurementJob = null
        scaleRepo.disconnect()
        _tareOffset.value = 0.0f
    }

    fun tareScale() {
        scaleRepo.tareScale()
        // Uppdatera offset baserat på den *råa* mätningen vid tareringstillfället
        _tareOffset.value = _rawMeasurement.value.weightGrams
        // Om inspelning pågår, lägg till en punkt med det nya (nära noll) värdet
        if (_isRecording.value && !_isPaused.value) {
            addSamplePoint(measurement.value)
        }
    }

    // --- Recording Functions ---
    fun startRecording() {
        if (_isRecording.value || connectionState.value !is BleConnectionState.Connected) return // Säkerställ att vi är anslutna
        _recordedSamplesFlow.value = emptyList()
        _recordingTimeMillis.value = 0L
        _isPaused.value = false
        _weightAtPause.value = null
        recordingStartTime = SystemClock.elapsedRealtime() // Nollställ starttid
        _isRecording.value = true
        addSamplePoint(measurement.value) // Lägg till första punkten (troligen 0g)
        startTimer()
    }

    fun pauseRecording() {
        if (!_isRecording.value || _isPaused.value) return
        _isPaused.value = true
        timePausedAt = SystemClock.elapsedRealtime()
        _weightAtPause.value = measurement.value.weightGrams // Spara vikten vid paus
        timerJob?.cancel() // Stoppa timern
    }

    fun resumeRecording() {
        if (!_isRecording.value || !_isPaused.value) return
        val pauseDuration = SystemClock.elapsedRealtime() - timePausedAt
        recordingStartTime += pauseDuration // Justera starttiden för att ignorera pausen
        _isPaused.value = false
        _weightAtPause.value = null
        startTimer() // Starta timern igen
    }

    suspend fun stopRecordingAndSave(setupState: BrewSetupState): Long? {
        if (!_isRecording.value && !_isPaused.value) return null // Kan bara spara om inspelning pågått

        // Spara aktuell tid och samples innan vi nollställer
        val finalTimeMillis = _recordingTimeMillis.value
        val finalSamples = _recordedSamplesFlow.value

        // Stoppa och nollställ inspelningsstate direkt
        stopRecording() // Detta nollställer _recordedSamplesFlow, _recordingTimeMillis etc.

        if (finalSamples.isEmpty()) {
            _error.value = "No data was recorded."
            return null
        }

        // Beräkna den faktiska starttiden i Realtid (System.currentTimeMillis)
        val actualStartTimeMillis = System.currentTimeMillis() - finalTimeMillis

        // Validera setupState innan vi sparar
        val beanId = setupState.selectedBean?.id
        val doseGrams = setupState.doseGrams.toDoubleOrNull()

        if (beanId == null || doseGrams == null) {
            _error.value = "Missing bean or dose to save."
            return null // Returnera null om setup är ogiltig
        }

        // Skapa Brew-objektet
        val newBrew = Brew(
            beanId = beanId,
            doseGrams = doseGrams,
            startedAt = Date(actualStartTimeMillis), // Använd beräknad starttid
            grinderId = setupState.selectedGrinder?.id,
            methodId = setupState.selectedMethod?.id,
            grindSetting = setupState.grindSetting.takeIf { it.isNotBlank() },
            grindSpeedRpm = setupState.grindSpeedRpm.toDoubleOrNull(),
            brewTempCelsius = setupState.brewTempCelsius.toDoubleOrNull(),
            notes = "Recorded from scale ${Date()}" // Enkel standardnotering
        )

        // Försök spara asynkront
        val result = viewModelScope.async {
            try {
                // Spara brew och samples i en transaktion
                val newId = coffeeRepo.addBrewWithSamples(newBrew, finalSamples)
                _error.value = null // Rensa ev. tidigare fel
                newId // Returnera det nya Brew ID:t
            } catch (e: Exception) {
                _error.value = "Could not save brew: ${e.message}"
                Log.e("ScaleViewModel", "Error saving brew: ${e.message}", e)
                null // Returnera null vid fel
            }
        }
        return result.await() // Vänta på att async-blocket blir klart
    }

    // --- Helper functions ---
    // Denna är nu public för att kunna anropas från LiveBrewScreen (Reset-knappen)
    fun stopRecording() {
        _isRecording.value = false
        _isPaused.value = false
        _weightAtPause.value = null
        timerJob?.cancel()
        _recordedSamplesFlow.value = emptyList() // Rensa samples
        _recordingTimeMillis.value = 0L // Nollställ tid
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_isRecording.value && !_isPaused.value) {
                // Uppdatera tiden baserat på SystemClock för precision
                _recordingTimeMillis.value = SystemClock.elapsedRealtime() - recordingStartTime
                delay(100) // Uppdatera ca 10 ggr/sekund
            }
        }
    }

    private fun addSamplePoint(measurement: ScaleMeasurement) {
        if (!_isRecording.value || _isPaused.value) return // Lägg bara till om vi spelar in aktivt

        val elapsedTimeMs = SystemClock.elapsedRealtime() - recordingStartTime
        val newSample = BrewSample(
            brewId = 0, // Sätts korrekt av repository/DAO vid insert
            timeMillis = elapsedTimeMs,
            // Avrunda till en decimal för att spara utrymme och undvika flyttalsproblem
            massGrams = String.format(Locale.US, "%.1f", measurement.weightGrams).toDouble(),
            flowRateGramsPerSecond = String.format(Locale.US, "%.1f", measurement.flowRateGramsPerSecond).toDouble()
        )
        // Lägg till det nya samplet i listan
        _recordedSamplesFlow.value = _recordedSamplesFlow.value + newSample
    }
}