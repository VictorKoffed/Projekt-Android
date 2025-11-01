package com.victorkoffed.projektandroid.ui.viewmodel.brew

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.victorkoffed.projektandroid.data.db.Bean
import com.victorkoffed.projektandroid.data.db.Brew
import com.victorkoffed.projektandroid.data.db.BrewMetrics
import com.victorkoffed.projektandroid.data.db.BrewSample
import com.victorkoffed.projektandroid.data.db.Grinder
import com.victorkoffed.projektandroid.data.db.Method
import com.victorkoffed.projektandroid.data.repository.CoffeeRepository
import com.victorkoffed.projektandroid.data.repository.ScaleRepository
import com.victorkoffed.projektandroid.domain.model.BleConnectionState
import com.victorkoffed.projektandroid.domain.model.ScaleMeasurement
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** Representerar inmatningsvärde och tillhörande fel. */
data class NumericInput(
    val value: String = "",
    val error: String? = null
)

/** Representerar all användarinmatning för en ny bryggning innan den sparas. */
data class BrewSetupState(
    val selectedBean: Bean? = null,
    val doseGrams: NumericInput = NumericInput(),
    val selectedGrinder: Grinder? = null,
    val grindSetting: String = "",
    val grindSpeedRpm: NumericInput = NumericInput(),
    val selectedMethod: Method? = null,
    val brewTempCelsius: NumericInput = NumericInput(),
    val notes: String = ""
)

/** Data class som returneras efter att en bryggning har sparats. */
data class SaveBrewResult(val brewId: Long?, val beanIdReachedZero: Long? = null)

// Lägg till en TAG konstant för Logcat-filtrering
private const val TAG = "BrewViewModel_DEBUG"

@HiltViewModel
class BrewViewModel @Inject constructor(
    private val repository: CoffeeRepository,
    private val scaleRepo: ScaleRepository // <-- NY INJEKTION
) : ViewModel() {

    private val decimalRegex = Regex("^\\d*\\.?\\d*$")
    private val integerRegex = Regex("^\\d*$")

    // --- State för dropdown-listor ---
    val availableBeans: StateFlow<List<Bean>> = repository.getAllBeans()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val availableGrinders: StateFlow<List<Grinder>> = repository.getAllGrinders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val availableMethods: StateFlow<List<Method>> = repository.getAllMethods()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- State för användarinmatning (Ersätter mutableStateOf) ---
    private val _brewSetupState = MutableStateFlow(BrewSetupState())
    // Den här exponeras för UI och ska observeras med .collectAsState()
    val brewSetupState: StateFlow<BrewSetupState> = _brewSetupState.asStateFlow()

    // --- State för felhantering vid sparande ---
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // --- State för att visa resultat ---
    private val _completedBrewMetrics = MutableStateFlow<BrewMetrics?>(null)
    private val _completedBrewSamples = MutableStateFlow<List<BrewSample>>(emptyList())
    // Denna variabel behövs inte för att visas, den fanns kvar av misstag.

    // --- State för att veta om det finns tidigare bryggningar ---
    val hasPreviousBrews: StateFlow<Boolean> = repository.getAllBrews()
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)


    // --- NYTT: State för inspelning (flyttat från ScaleViewModel) ---
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

    private var manualTimerJob: Job? = null // Används för att simulera tid vid disconnect

    init {
        // Lyssna på justerade mätdata från repon för inspelning
        viewModelScope.launch {
            scaleRepo.observeMeasurements()
                // .catch { ... } // <-- BORTTAGEN: Har ingen effekt på StateFlow
                .collect { measurementData ->
                    // ★★★ FIX: Hantera fel i collectorn ★★★
                    try {
                        handleScaleTimer()
                        if (_isRecording.value && !_isPaused.value) {
                            addSamplePoint(measurementData)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing measurement data", e)
                    }
                }
        }

        // Lyssna på anslutningsstatus för att hantera frånkopplingar
        viewModelScope.launch {
            scaleRepo.observeConnectionState()
                .collect { state ->
                    // Hämta det senaste justerade mätvärdet från repon
                    val latestMeasurement = scaleRepo.observeMeasurements().value
                    handleConnectionStateChange(state, latestMeasurement)
                }
        }
    }

    /** Hanterar anslutningsändringar MEDAN en session pågår. */
    private fun handleConnectionStateChange(state: BleConnectionState, latestMeasurement: ScaleMeasurement) {
        if ((state is BleConnectionState.Disconnected || state is BleConnectionState.Error)) {
            // Om vi kopplas från under inspelning (ej pausad)
            if (_isRecording.value && !_isPaused.value) {
                _isRecordingWhileDisconnected.value = true
                _weightAtPause.value = latestMeasurement.weightGrams
                Log.w(TAG, "Recording... DISCONNECTED. Data collection paused, Timer continues.")
            }
            // Om vi kopplas från under manuell paus
            else if (_isRecording.value && _isPaused.value) {
                _isRecordingWhileDisconnected.value = true
                Log.w(TAG, "Manually Paused AND Disconnected.")
            }
        }
        // Om vi återansluter
        else if (state is BleConnectionState.Connected) {
            if (_isRecordingWhileDisconnected.value) {
                _isRecordingWhileDisconnected.value = false
                Log.d(TAG, "Reconnected while recording. Data collection resumes.")
                // Timern (manualTimerJob) har fortsatt att ticka, så ingen åtgärd behövs där.
                // Repositoryt har redan nollställt tare-offset vid anslutning,
                // ScaleViewModel kommer att justera offseten vid återanslutning.
            }
        }
    }


    // --- Funktioner för att uppdatera inställningar ---
    fun selectBean(bean: Bean?) {
        _brewSetupState.update { it.copy(selectedBean = bean) }
    }

    fun onDoseChange(dose: String) {
        val isValid = dose.matches(decimalRegex)
        _brewSetupState.update {
            it.copy(
                doseGrams = it.doseGrams.copy(
                    value = dose,
                    error = if (isValid || dose.isBlank()) null else "Måste vara ett giltigt tal (t.ex. 20.5)"
                )
            )
        }
    }

    fun selectGrinder(grinder: Grinder?) {
        _brewSetupState.update { it.copy(selectedGrinder = grinder) }
    }

    fun onGrindSettingChange(setting: String) {
        _brewSetupState.update { it.copy(grindSetting = setting) }
    }

    fun onGrindSpeedChange(rpm: String) {
        val isValid = rpm.matches(integerRegex)
        _brewSetupState.update {
            it.copy(
                grindSpeedRpm = it.grindSpeedRpm.copy(
                    value = rpm,
                    error = if (isValid || rpm.isBlank()) null else "Måste vara ett heltal"
                )
            )
        }
    }

    fun selectMethod(method: Method?) {
        _brewSetupState.update { it.copy(selectedMethod = method) }
    }

    fun onBrewTempChange(temp: String) {
        val isValid = temp.matches(decimalRegex)
        _brewSetupState.update {
            it.copy(
                brewTempCelsius = it.brewTempCelsius.copy(
                    value = temp,
                    error = if (isValid || temp.isBlank()) null else "Måste vara ett giltigt tal (t.ex. 94.5)"
                )
            )
        }
    }

    fun isSetupValid(): Boolean {
        // Läs det aktuella state-värdet
        val currentState = _brewSetupState.value

        val doseValue = currentState.doseGrams.value.toDoubleOrNull()
        val doseValid = doseValue?.let { it > 0 } == true

        val noInputErrors = currentState.doseGrams.error == null &&
                currentState.grindSpeedRpm.error == null &&
                currentState.brewTempCelsius.error == null

        return currentState.selectedBean != null &&
                currentState.selectedMethod != null &&
                doseValid &&
                noInputErrors
    }

    // Returnerar det aktuella StateFlow-värdet (värdet i StateFlow)
    fun getCurrentSetup(): BrewSetupState { return _brewSetupState.value }

    // --- Funktioner för resultat ---
    fun loadBrewResults(brewId: Long?) {
        if (brewId == null) {
            _completedBrewMetrics.value = null
            _completedBrewSamples.value = emptyList()
            return
        }

        viewModelScope.launch {
            repository.getBrewMetrics(brewId)
                .catch { _ -> _completedBrewMetrics.value = null }
                .collectLatest { metrics ->
                    _completedBrewMetrics.value = metrics
                }
        }
        viewModelScope.launch {
            repository.getSamplesForBrew(brewId)
                .catch { _ -> _completedBrewSamples.value = emptyList() }
                .collectLatest { samples ->
                    _completedBrewSamples.value = samples
                }
        }
    }

    fun clearBrewResults() {
        loadBrewResults(null)
    }


    // --- Funktion: Ladda inställningar från senaste bryggningen ---
    fun loadLatestBrewSettings() {
        viewModelScope.launch {
            val latestBrew = repository.getAllBrews().firstOrNull()?.firstOrNull()

            if (latestBrew != null) {
                val bean = repository.getBeanById(latestBrew.beanId)
                val grinder = latestBrew.grinderId?.let { repository.getGrinderById(it) }
                val method = latestBrew.methodId?.let { repository.getMethodById(it) }

                // Uppdatera StateFlow-värdet
                _brewSetupState.update {
                    it.copy(
                        selectedBean = bean,
                        doseGrams = NumericInput(latestBrew.doseGrams.toString()),
                        selectedGrinder = grinder,
                        grindSetting = latestBrew.grindSetting ?: "",
                        grindSpeedRpm = NumericInput(latestBrew.grindSpeedRpm?.toInt()?.toString() ?: ""),
                        selectedMethod = method,
                        brewTempCelsius = NumericInput(latestBrew.brewTempCelsius?.toString() ?: ""),
                        notes = ""
                    )
                }
            }
        }
    }

    // --- NYTT: Inspelningsfunktioner (flyttade från ScaleViewModel) ---

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
        if (scaleRepo.observeConnectionState().value !is BleConnectionState.Connected) {
            _error.value = "Scale not connected."; return
        }
        scaleRepo.tareScale()
        if (_isRecording.value && !_isPaused.value) {
            addSamplePoint(scaleRepo.observeMeasurements().value)
        }
    }

    fun startRecording() {
        if (_isRecording.value || _isPaused.value || _countdown.value != null) return
        if (scaleRepo.observeConnectionState().value !is BleConnectionState.Connected) {
            _error.value = "Scale not connected."; return
        }

        viewModelScope.launch {
            try {
                _countdown.value = 3; delay(1000L)
                _countdown.value = 2; delay(1000L)
                _countdown.value = 1; delay(1000L)

                // Anropa repon för att tarera OCH starta timer
                scaleRepo.tareScaleAndStartTimer()
                delay(150L) // Ge vågen tid att reagera
                _countdown.value = null
                internalStartRecording()
            } catch (e: Exception) {
                Log.e(TAG, "Error starting recording", e)
                _countdown.value = null; _error.value = "Could not start."
                if (scaleRepo.observeConnectionState().value is BleConnectionState.Connected) scaleRepo.resetTimer()
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
        _weightAtPause.value = scaleRepo.observeMeasurements().value.weightGrams

        if (scaleRepo.observeConnectionState().value is BleConnectionState.Connected) {
            scaleRepo.stopTimer()
        }
        Log.d(TAG, "Manually paused. Manual timer stopped.")
    }

    fun resumeRecording() {
        if (!_isRecording.value || !_isPaused.value) return

        _isPaused.value = false
        _isRecordingWhileDisconnected.value = false
        _weightAtPause.value = null

        if (scaleRepo.observeConnectionState().value is BleConnectionState.Connected) {
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

        if (wasRecordingOrPaused && scaleRepo.observeConnectionState().value is BleConnectionState.Connected) {
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

    // --- Slut på flyttade funktioner ---

    // --- Funktion: Spara bryggning utan grafer ---
    suspend fun saveBrewWithoutSamples(): Long? {
        if (!isSetupValid()) {
            return null
        }

        // Hämta det aktuella state-värdet för sparande
        val currentSetup = _brewSetupState.value

        val newBrew = Brew(
            beanId = currentSetup.selectedBean!!.id,
            doseGrams = currentSetup.doseGrams.value.toDouble(),
            startedAt = Date(System.currentTimeMillis()),
            grinderId = currentSetup.selectedGrinder?.id,
            methodId = currentSetup.selectedMethod!!.id,
            grindSetting = currentSetup.grindSetting.takeIf { it.isNotBlank() },
            grindSpeedRpm = currentSetup.grindSpeedRpm.value.toDoubleOrNull(),
            brewTempCelsius = currentSetup.brewTempCelsius.value.toDoubleOrNull(),
            notes = currentSetup.notes.takeIf { it.isNotBlank() }
        )

        return viewModelScope.async {
            try {
                repository.addBrew(newBrew)
            } catch (_: Exception) {
                null
            }
        }.await()
    }

    /**
     * Sparar en live-bryggning.
     */
    suspend fun saveLiveBrew(
        setupState: BrewSetupState,
        finalSamples: List<BrewSample>, // Får nu detta från sig själv
        finalTimeMillis: Long, // Får nu detta från sig själv
        scaleDeviceName: String?
    ): SaveBrewResult {
        // [LOG 1: Start av funktionen]
        Log.d(TAG, "saveLiveBrew: Start. Time: $finalTimeMillis ms, Samples size: ${finalSamples.size}")

        if (finalSamples.size < 2 || finalTimeMillis <= 0) {
            _error.value = "Not enough data recorded to save."
            // [LOG 2: Fel vid otillräcklig data]
            Log.e(TAG, "saveLiveBrew: FEL - Otillräcklig data. Samples: ${finalSamples.size}, Time: $finalTimeMillis ms")
            return SaveBrewResult(null)
        }

        // Validera Setup
        val beanId = setupState.selectedBean?.id
        val doseGrams = setupState.doseGrams.value.toDoubleOrNull()
        if (beanId == null || doseGrams == null) {
            _error.value = "Missing bean/dose in setup."
            // [LOG 3: Fel vid validering av setup]
            Log.e(TAG, "saveLiveBrew: FEL - Setup ogiltig. BeanId: $beanId, Dose: $doseGrams")
            return SaveBrewResult(null)
        }

        // Skapa Brew-objekt
        val actualStartTimeMillis = System.currentTimeMillis() - finalTimeMillis
        val scaleInfo = scaleDeviceName?.let { " via $it" } ?: ""
        val newBrew = Brew(
            beanId = beanId,
            doseGrams = doseGrams,
            startedAt = Date(actualStartTimeMillis),
            grinderId = setupState.selectedGrinder?.id,
            methodId = setupState.selectedMethod?.id,
            grindSetting = setupState.grindSetting.takeIf { it.isNotBlank() },
            grindSpeedRpm = setupState.grindSpeedRpm.value.toDoubleOrNull(),
            brewTempCelsius = setupState.brewTempCelsius.value.toDoubleOrNull(),
            notes = "Recorded${scaleInfo} on ${
                SimpleDateFormat(
                    "yyyy-MM-dd HH:mm",
                    Locale.getDefault()
                ).format(Date())
            }"
        )
        // [LOG 4: Kontroll av det skapade Brew-objektet]
        Log.d(TAG, "saveLiveBrew: Brew-objekt skapat. BeanId: $beanId, Dose: $doseGrams, MethodId: ${newBrew.methodId}, GrindSetting: ${newBrew.grindSetting}, StartTid: ${newBrew.startedAt}")
        Log.d(TAG, "saveLiveBrew: Första Sample: t=${finalSamples.firstOrNull()?.timeMillis}ms, mass=${finalSamples.firstOrNull()?.massGrams}g")


        // Spara Brew och Samples (med transaktion)
        val savedBrewId: Long? = viewModelScope.async {
            try {
                // [LOG 5: Före databastransaktion]
                Log.d(TAG, "saveLiveBrew: Startar repository-transaktion (addBrewWithSamples)...")
                val id = repository.addBrewWithSamples(newBrew, finalSamples)
                // [LOG 6: Efter framgångsrik databastransaktion]
                Log.d(TAG, "saveLiveBrew: Repository-transaktion LYCKADES. Ny BrewId: $id")
                clearError()
                id
            } catch (e: Exception) {
                // [LOG 7: Databasfel]
                Log.e(TAG, "saveLiveBrew: Repository-transaktion MISSLYCKADES under spara!", e)
                _error.value = "Save failed: ${e.message}"
                null
            }
        }.await()

        // [LOG 8: Kontroll av resultatet efter await]
        if (savedBrewId == null) {
            Log.w(TAG, "saveLiveBrew: BrewId är null, sparandet misslyckades på DB-nivå. Returnerar null.")
            return SaveBrewResult(null)
        }

        var beanIdReachedZero: Long? = null
        try {
            val bean = repository.getBeanById(beanId)
            if (bean != null && bean.remainingWeightGrams <= 0.0 && !bean.isArchived) {
                beanIdReachedZero = beanId
            }
        } catch (e: Exception) {
            // Fånga eventuella fel vid kontroll av bönans saldo
            Log.e(TAG, "saveLiveBrew: Fel vid kontroll av bönans saldo efter sparande", e)
        }

        // [LOG 9: Slut på funktionen]
        Log.d(TAG, "saveLiveBrew: Slut. Återvänder BrewId: $savedBrewId, BeanIdReachedZero: $beanIdReachedZero")
        return SaveBrewResult(brewId = savedBrewId, beanIdReachedZero = beanIdReachedZero)
    }

    fun clearError() {
        _error.value = null
    }

    override fun onCleared() {
        super.onCleared()
        // Stoppa eventuella timers om denna VM förstörs
        manualTimerJob?.cancel()
    }
}