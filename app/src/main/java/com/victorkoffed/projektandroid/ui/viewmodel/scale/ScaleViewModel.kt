// app/src/main/java/com/victorkoffed/projektandroid/ui/viewmodel/scale/ScaleViewModel.kt
package com.victorkoffed.projektandroid.ui.viewmodel.scale

// --- Kärnbibliotek ---
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.util.Log
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope

// --- Databas & Repository ---
import com.victorkoffed.projektandroid.data.db.Brew
import com.victorkoffed.projektandroid.data.db.BrewSample
import com.victorkoffed.projektandroid.data.repository.CoffeeRepository
import com.victorkoffed.projektandroid.data.repository.ScaleRepository

// --- Domänmodeller ---
import com.victorkoffed.projektandroid.domain.model.BleConnectionState
import com.victorkoffed.projektandroid.domain.model.DiscoveredDevice
import com.victorkoffed.projektandroid.domain.model.ScaleMeasurement

// --- Andra ViewModels (för dataklasser) ---
import com.victorkoffed.projektandroid.ui.viewmodel.brew.BrewSetupState

// --- Hilt ---
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

// --- Coroutines & Flow ---
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
import kotlinx.coroutines.flow.map // <-- Se till att denna import finns
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

// --- Övrigt ---
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.seconds


// Konstanter för SharedPreferences
private const val PREFS_NAME = "ScalePrefs"
private const val PREF_REMEMBERED_SCALE_ADDRESS = "remembered_scale_address"
private const val PREF_REMEMBER_SCALE_ENABLED = "remember_scale_enabled"
// NY KONSTANT för auto-connect
private const val PREF_AUTO_CONNECT_ENABLED = "auto_connect_enabled"


// Dataklass för returvärdet från stopRecordingAndSave
data class SaveBrewResult(
    val brewId: Long?,
    val beanIdReachedZero: Long? = null
)

/**
 * Privat objekt för att översätta tekniska BLE-felmeddelanden
 * till användarvänliga strängar.
 */
private object BleErrorTranslator {
    fun translate(rawMessage: String?): String {
        if (rawMessage == null) return "Ett okänt fel uppstod."

        return when {
            rawMessage.contains("GATT Error (133)") -> "Anslutningen misslyckades (GATT 133). Kontrollera att vågen är nära och påslagen."
            rawMessage.contains("GATT Error (8)") -> "Anslutningen tappades oväntat (GATT 8). Vågen kan vara utom räckhåll."
            rawMessage.contains("GATT Error (19)") -> "Anslutningen tappades (GATT 19). Vågen stängdes möjligen av."
            rawMessage.contains("GATT Error") -> "Ett anslutningsfel uppstod. Försök ansluta igen."
            rawMessage.contains("Could not find services") -> "Kunde inte hitta vågens tjänster. Prova att starta om vågen."
            rawMessage.contains("Scale characteristic not found") -> "Ansluten enhet verkar inte vara en kompatibel våg."
            rawMessage.contains("Could not enable notifications") -> "Kunde inte aktivera dataström från vågen."
            rawMessage.contains("Okänt skanningsfel") -> "Ett fel uppstod vid sökning efter enheter." // Egen översättning
            rawMessage.contains("BLE scan failed") -> "Sökningen misslyckades. Kontrollera att Bluetooth är på."
            rawMessage.contains("Bluetooth is not enabled") -> "Bluetooth är inte påslaget."
            rawMessage.contains("Missing BLUETOOTH_SCAN permission") -> "Appen saknar behörighet att söka efter enheter."
            // Lägg till fler översättningar här vid behov
            else -> rawMessage // Fallback till det råa meddelandet om det är okänt
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

    // --- Scanning State ---
    private val _devices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val devices: StateFlow<List<DiscoveredDevice>> = _devices
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning
    private var scanJob: Job? = null

    // --- Connection and Measurement State ---
    // Mappar flödet för att översätta felmeddelanden direkt.
    val connectionState: SharedFlow<BleConnectionState> = scaleRepo.observeConnectionState()
        .map { state ->
            if (state is BleConnectionState.Error) {
                BleConnectionState.Error(BleErrorTranslator.translate(state.message)) // Översätt här
            } else {
                state
            }
        }
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


    // --- Error State (Används för Skan/Spara-fel etc.) ---
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error // Denna observeras nu av MainActivity

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

    // --- Remember & Auto-Connect Scale State ---
    private val _rememberScaleEnabled = MutableStateFlow(
        sharedPreferences.getBoolean(PREF_REMEMBER_SCALE_ENABLED, false)
    )
    val rememberScaleEnabled: StateFlow<Boolean> = _rememberScaleEnabled.asStateFlow()

    private val _autoConnectEnabled = MutableStateFlow(
        sharedPreferences.getBoolean(PREF_AUTO_CONNECT_ENABLED, _rememberScaleEnabled.value)
    )
    val autoConnectEnabled: StateFlow<Boolean> = _autoConnectEnabled.asStateFlow()


    private val _rememberedScaleAddress = MutableStateFlow(loadRememberedScaleAddress())
    val rememberedScaleAddress: StateFlow<String?> = _rememberedScaleAddress.asStateFlow()

    private var recordingStartTime: Long = 0L
    private var timePausedAt: Long = 0L
    private var measurementJob: Job? = null
    private var timerJob: Job? = null

    init {
        attemptAutoConnect()
    }

    // ---------------------------------------------------------------------------------------------
    // --- Funktionalitet för Skanning och Anslutning ---
    // ---------------------------------------------------------------------------------------------
    fun startScan() {
        if (_isScanning.value) return

        _devices.value = emptyList()
        clearError() // Rensa gamla fel (_error)
        _isScanning.value = true
        reconnectAttempted = false

        scanJob?.cancel()

        scanJob = viewModelScope.launch {
            try {
                withTimeoutOrNull(10.seconds) {
                    scaleRepo.startScanDevices()
                        .catch { e ->
                            _error.value = BleErrorTranslator.translate(e.message ?: "Okänt skanningsfel") // Uppdatera _error
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
        }
        if (_isScanning.value) {
            _isScanning.value = false
            Log.d("ScaleViewModel", "Skanning stoppades manuellt av användaren.")
        }
    }


    fun connect(device: DiscoveredDevice) {
        stopScan()
        _tareOffset.value = 0.0f
        isManualDisconnect = false
        reconnectAttempted = false
        clearError() // Rensa _error
        scaleRepo.connect(device.address) // connectionState uppdateras via callback
    }

    fun disconnect() {
        Log.d("ScaleViewModel", "Manuell frånkoppling initierad.")
        isManualDisconnect = true
        reconnectAttempted = false
        stopRecording()
        measurementJob?.cancel(); measurementJob = null
        scaleRepo.disconnect() // connectionState uppdateras via callback
        _tareOffset.value = 0.0f
    }

    fun tareScale() {
        // Inga fel genereras direkt här som behöver visas i Snackbar
        scaleRepo.tareScale()
        _tareOffset.value = _rawMeasurement.value.weightGrams
        Log.d("ScaleViewModel", "Tare anropad. Ny offset: ${_tareOffset.value}g. Aktuell vikt: ${measurement.value.weightGrams}g")
        if (_isRecording.value && !_isPaused.value) {
            addSamplePoint(measurement.value)
        }
    }


    // ---------------------------------------------------------------------------------------------
    // --- Inspelningsfunktionalitet (Brew Recording) ---
    // ---------------------------------------------------------------------------------------------
    fun startRecording() {
        if (_isRecording.value || _isPaused.value || _countdown.value != null) { Log.w("ScaleViewModel", "Start inspelning anropades men är redan upptagen."); return }
        if (connectionState.replayCache.lastOrNull() !is BleConnectionState.Connected) {
            _error.value = "Kan inte starta inspelning, vågen är inte ansluten." // Uppdatera _error
            return
        }
        viewModelScope.launch {
            try {
                Log.d("ScaleViewModel", "Initierar inspelningssekvens...")
                tareScale()
                delay(200L)
                _countdown.update { 3 }; delay(1000L)
                _countdown.update { 2 }; delay(1000L)
                _countdown.update { 1 }; delay(1000L)
                _countdown.update { null }
                Log.d("ScaleViewModel", "Nedräkning klar. Startar inspelning.")
                internalStartRecording()
            } catch (e: Exception) {
                Log.e("ScaleViewModel", "Fel under inspelningsinitiering", e);
                _countdown.value = null;
                _error.value = "Kunde inte starta inspelning." // Uppdatera _error
            }
        }
    }
    private fun internalStartRecording() {
        _recordedSamplesFlow.value = emptyList(); _recordingTimeMillis.value = 0L; _isPaused.value = false; _weightAtPause.value = null; recordingStartTime = SystemClock.elapsedRealtime(); _isRecording.value = true
        addSamplePoint(measurement.value)
        startTimer()
    }
    fun pauseRecording() { if (!_isRecording.value || _isPaused.value) return; _isPaused.value = true; timePausedAt = SystemClock.elapsedRealtime(); _weightAtPause.value = measurement.value.weightGrams; timerJob?.cancel() }
    fun resumeRecording() { if (!_isRecording.value || !_isPaused.value) return; val pauseDuration = SystemClock.elapsedRealtime() - timePausedAt; recordingStartTime += pauseDuration; _isPaused.value = false; _weightAtPause.value = null; startTimer() }

    suspend fun stopRecordingAndSave(setupState: BrewSetupState): SaveBrewResult {
        if (_countdown.value != null) { stopRecording(); return SaveBrewResult(null) }
        if (!_isRecording.value && !_isPaused.value) return SaveBrewResult(null)

        val finalTimeMillis = _recordingTimeMillis.value
        val finalSamples = _recordedSamplesFlow.value
        stopRecording()

        if (finalSamples.isEmpty()) {
            _error.value = "Ingen data spelades in." // Uppdatera _error
            return SaveBrewResult(null)
        }

        val actualStartTimeMillis = System.currentTimeMillis() - finalTimeMillis

        val beanId = setupState.selectedBean?.id
        val doseGrams = setupState.doseGrams.toDoubleOrNull()

        if (beanId == null || doseGrams == null) {
            _error.value = "Saknar bönor eller dos för att spara." // Uppdatera _error
            return SaveBrewResult(null)
        }

        val currentScaleState = connectionState.replayCache.lastOrNull()
        val scaleInfo = if (currentScaleState is BleConnectionState.Connected) " via ${currentScaleState.deviceName}" else ""


        val newBrew = Brew(
            beanId = beanId,
            doseGrams = doseGrams,
            startedAt = Date(actualStartTimeMillis),
            grinderId = setupState.selectedGrinder?.id,
            methodId = setupState.selectedMethod?.id,
            grindSetting = setupState.grindSetting.takeIf { it.isNotBlank() },
            grindSpeedRpm = setupState.grindSpeedRpm.toDoubleOrNull(),
            brewTempCelsius = setupState.brewTempCelsius.toDoubleOrNull(),
            notes = "Recorded${scaleInfo} on ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())}"
        )

        val savedBrewId: Long? = viewModelScope.async {
            try {
                val newId = coffeeRepo.addBrewWithSamples(newBrew, finalSamples)
                clearError() // Rensa _error vid lyckad sparande
                newId
            } catch (e: Exception) {
                _error.value = "Kunde inte spara bryggning: ${e.message}" // Uppdatera _error
                Log.e("ScaleViewModel", "Fel vid sparning av bryggning: ${e.message}", e)
                null
            }
        }.await()


        var beanIdReachedZero: Long? = null
        if (savedBrewId != null) {
            try {
                val updatedBean = coffeeRepo.getBeanById(beanId)
                if (updatedBean != null && updatedBean.remainingWeightGrams <= 0.0 && !updatedBean.isArchived) {
                    beanIdReachedZero = beanId
                    Log.d("ScaleViewModel", "Bean $beanId reached zero or less after saving brew $savedBrewId.")
                }
            } catch (e: Exception) {
                Log.e("ScaleViewModel", "Kunde inte hämta böna $beanId efter sparande för att kolla vikt.", e)
            }
        }

        return SaveBrewResult(brewId = savedBrewId, beanIdReachedZero = beanIdReachedZero)
    }


    fun stopRecording() { _countdown.value = null; _isRecording.value = false; _isPaused.value = false; _weightAtPause.value = null; timerJob?.cancel(); _recordedSamplesFlow.value = emptyList(); _recordingTimeMillis.value = 0L }
    private fun startTimer() { timerJob?.cancel(); timerJob = viewModelScope.launch { while (_isRecording.value && !_isPaused.value) { _recordingTimeMillis.value = SystemClock.elapsedRealtime() - recordingStartTime; delay(100) } } }

    private fun addSamplePoint(measurement: ScaleMeasurement) {
        if (!_isRecording.value || _isPaused.value) return
        val elapsedTimeMs = SystemClock.elapsedRealtime() - recordingStartTime
        if (elapsedTimeMs < 0) return

        val newSample = BrewSample(
            brewId = 0,
            timeMillis = elapsedTimeMs,
            massGrams = String.format(Locale.US, "%.1f", measurement.weightGrams).toDouble(),
            flowRateGramsPerSecond = String.format(Locale.US, "%.1f", measurement.flowRateGramsPerSecond).toDouble()
        )
        _recordedSamplesFlow.update { it + newSample }
    }



    // ---------------------------------------------------------------------------------------------
    // --- Funktionalitet för 'Kom Ihåg Våg' & Auto-Connect Preferenser ---
    // ---------------------------------------------------------------------------------------------

    fun setRememberScaleEnabled(enabled: Boolean) {
        sharedPreferences.edit { putBoolean(PREF_REMEMBER_SCALE_ENABLED, enabled) }
        _rememberScaleEnabled.value = enabled
        Log.d("ScaleViewModel", "Set 'remember scale' to: $enabled")

        if (!enabled) {
            setAutoConnectEnabled(false)
            saveRememberedScaleAddress(null)
        } else {
            val currentState = connectionState.replayCache.lastOrNull()
            if (currentState is BleConnectionState.Connected) {
                saveRememberedScaleAddress(currentState.deviceAddress)
                setAutoConnectEnabled(true)
            } else {
                Log.w("ScaleViewModel", "Tried to remember scale address, but not connected.")
                setAutoConnectEnabled(false)
            }
        }
    }

    fun setAutoConnectEnabled(enabled: Boolean) {
        val canEnable = _rememberScaleEnabled.value
        val newValue = enabled && canEnable

        sharedPreferences.edit { putBoolean(PREF_AUTO_CONNECT_ENABLED, newValue) }
        _autoConnectEnabled.value = newValue
        Log.d("ScaleViewModel", "Set 'auto connect' to: $newValue (Remember enabled: $canEnable)")
    }


    private fun saveRememberedScaleAddress(address: String?) {
        if (address != null && _rememberScaleEnabled.value) {
            sharedPreferences.edit { putString(PREF_REMEMBERED_SCALE_ADDRESS, address) }
            Log.d("ScaleViewModel", "Saved scale address: $address")
            _rememberedScaleAddress.value = address
        } else {
            if (sharedPreferences.contains(PREF_REMEMBERED_SCALE_ADDRESS)) {
                sharedPreferences.edit { remove(PREF_REMEMBERED_SCALE_ADDRESS) }
                Log.d("ScaleViewModel", "Cleared saved scale address.")
            }
            _rememberedScaleAddress.value = null
            setAutoConnectEnabled(false)
        }
    }


    private fun loadRememberedScaleAddress(): String? {
        return if (_rememberScaleEnabled.value) {
            sharedPreferences.getString(PREF_REMEMBERED_SCALE_ADDRESS, null)
        } else {
            null
        }
    }

    fun forgetRememberedScale() {
        Log.d("ScaleViewModel", "Manually forgetting scale.")
        setRememberScaleEnabled(false)
    }


    private fun attemptAutoConnect() {
        if (!_rememberScaleEnabled.value || !_autoConnectEnabled.value) {
            Log.d("ScaleViewModel", "Auto-connect skipped (Remember: ${_rememberScaleEnabled.value}, AutoConnect: ${_autoConnectEnabled.value}).")
            return
        }
        val lastState = connectionState.replayCache.lastOrNull()
        if (lastState is BleConnectionState.Connected || lastState is BleConnectionState.Connecting) {
            Log.d("ScaleViewModel", "Already connected or connecting, skipping auto-connect.")
            reconnectAttempted = false
            return
        }

        val rememberedAddress = loadRememberedScaleAddress()
        if (rememberedAddress != null) {
            Log.i("ScaleViewModel", "Attempting auto-connect to saved address: $rememberedAddress")
            isManualDisconnect = false
            clearError() // Rensa _error
            scaleRepo.connect(rememberedAddress) // connectionState uppdateras
        } else {
            Log.d("ScaleViewModel", "No saved scale address found for auto-connect.")
        }
    }

    // ---------------------------------------------------------------------------------------------
    // --- Hantering av Anslutningsstatus ---
    // ---------------------------------------------------------------------------------------------
    private fun handleConnectionStateChange(state: BleConnectionState) {
        when (state) {
            is BleConnectionState.Connected -> {
                Log.i("ScaleViewModel", "Connected to ${state.deviceName} (${state.deviceAddress}).")
                reconnectAttempted = false
                if (_rememberScaleEnabled.value) {
                    saveRememberedScaleAddress(state.deviceAddress)
                }
                isManualDisconnect = false
                clearError() // Rensa _error vid lyckad anslutning

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

                if(!isManualDisconnect && (_isRecording.value || _isPaused.value || _countdown.value != null)) {
                    Log.w("ScaleViewModel", "Unexpected disconnect during recording/countdown. Stopping.")
                    stopRecording()
                }

                if (!isManualDisconnect &&
                    _rememberScaleEnabled.value &&
                    _autoConnectEnabled.value &&
                    connectionState.replayCache.lastOrNull() !is BleConnectionState.Connecting &&
                    !reconnectAttempted)
                {
                    viewModelScope.launch {
                        Log.d("ScaleViewModel", "Unexpected disconnect with auto-connect enabled. Waiting 2 seconds before attempting ONE reconnect...")
                        delay(2000L)
                        if (connectionState.replayCache.lastOrNull() is BleConnectionState.Disconnected &&
                            _rememberScaleEnabled.value &&
                            _autoConnectEnabled.value &&
                            !reconnectAttempted)
                        {
                            Log.i("ScaleViewModel", "Still disconnected. Attempting single auto-reconnect.")
                            reconnectAttempted = true
                            attemptAutoConnect()
                        } else {
                            Log.d("ScaleViewModel", "State changed, remember/auto-connect disabled, connection in progress, or reconnect already attempted. Skipping auto-reconnect attempt.")
                        }
                    }
                } else {
                    Log.d("ScaleViewModel", "Skipping auto-reconnect attempt. Reason: " +
                            "ManualDisconnect=$isManualDisconnect, " +
                            "RememberEnabled=${_rememberScaleEnabled.value}, " +
                            "AutoConnectEnabled=${_autoConnectEnabled.value}, " +
                            "IsConnecting=${connectionState.replayCache.lastOrNull() is BleConnectionState.Connecting}, " +
                            "ReconnectAttempted=$reconnectAttempted")
                }
            }

            is BleConnectionState.Error -> {
                // Felet (state.message) är redan översatt och kommer visas globalt via MainActivity
                Log.e("ScaleViewModel", "Connection error (user message): ${state.message}")
                measurementJob?.cancel(); measurementJob = null

                if(_isRecording.value || _isPaused.value || _countdown.value != null) {
                    Log.w("ScaleViewModel", "Connection error during recording/countdown. Stopping.")
                    stopRecording()
                }
                if (!isManualDisconnect &&
                    _rememberScaleEnabled.value &&
                    _autoConnectEnabled.value &&
                    connectionState.replayCache.lastOrNull() !is BleConnectionState.Connecting &&
                    !reconnectAttempted)
                {
                    viewModelScope.launch {
                        Log.d("ScaleViewModel", "Connection Error with auto-connect enabled. Waiting 2 seconds before attempting ONE reconnect...")
                        delay(2000L)
                        if (connectionState.replayCache.lastOrNull() !is BleConnectionState.Connected &&
                            connectionState.replayCache.lastOrNull() !is BleConnectionState.Connecting &&
                            _rememberScaleEnabled.value &&
                            _autoConnectEnabled.value &&
                            !reconnectAttempted)
                        {
                            Log.i("ScaleViewModel", "Still not connected after error. Attempting single auto-reconnect.")
                            reconnectAttempted = true
                            attemptAutoConnect()
                        } else {
                            Log.d("ScaleViewModel", "State changed, remember/auto-connect disabled, connection in progress/successful, or reconnect already attempted. Skipping auto-reconnect attempt after error.")
                        }
                    }
                } else {
                    Log.d("ScaleViewModel", "Skipping auto-reconnect attempt after error. Reason: " +
                            "ManualDisconnect=$isManualDisconnect, " +
                            "RememberEnabled=${_rememberScaleEnabled.value}, " +
                            "AutoConnectEnabled=${_autoConnectEnabled.value}, " +
                            "IsConnecting=${connectionState.replayCache.lastOrNull() is BleConnectionState.Connecting}, " +
                            "ReconnectAttempted=$reconnectAttempted")
                }

                isManualDisconnect = false
            }
            is BleConnectionState.Connecting -> {
                Log.d("ScaleViewModel", "Connecting...")
                clearError() // Rensa _error vid nytt anslutningsförsök
            }
        }
    }

    /**
     * Initierar ett nytt försök att ansluta till den sparade vågen.
     */
    fun retryConnection() {
        Log.i("ScaleViewModel", "Manual retry connection triggered.")
        clearError() // Rensa _error
        reconnectAttempted = false

        val rememberedAddress = loadRememberedScaleAddress()
        if (rememberedAddress != null) {
            val lastState = connectionState.replayCache.lastOrNull()
            if (lastState is BleConnectionState.Connected || lastState is BleConnectionState.Connecting) {
                Log.d("ScaleViewModel", "Manual retry skipped: Already connected or connecting.")
                return
            }
            Log.i("ScaleViewModel", "Attempting manual connect to saved address: $rememberedAddress")
            isManualDisconnect = false
            scaleRepo.connect(rememberedAddress) // connectionState uppdateras
        } else {
            Log.w("ScaleViewModel", "Manual retry failed: No remembered scale address found.")
            _error.value = "No scale remembered to connect to." // Uppdatera _error
        }
    }


    /**
     * Nollställer det generella _error-state. Anropas från MainActivity efter att felet visats.
     */
    fun clearError() {
        _error.value = null
    }

    override fun onCleared() {
        super.onCleared()
        Log.d("ScaleViewModel", "onCleared called. Stopping scan and disconnecting.")
        stopScan()
        if (connectionState.replayCache.lastOrNull() !is BleConnectionState.Disconnected) {
            isManualDisconnect = true
            disconnect()
        }
        measurementJob?.cancel()
        timerJob?.cancel()
        scanJob?.cancel()
    }
}