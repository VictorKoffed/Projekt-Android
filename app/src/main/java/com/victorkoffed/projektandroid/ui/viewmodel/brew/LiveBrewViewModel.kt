/*
 * Referensnotering (AI-assistans): Den komplexa tillståndslogiken (hantering av
 * _isRecordingWhileDisconnected, _weightAtPause, och den manuella timerns coroutine)
 * har utvecklats med AI-assistans för att säkerställa robust inspelning. Se README.md.
 */

package com.victorkoffed.projektandroid.ui.viewmodel.brew

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.victorkoffed.projektandroid.data.db.Brew
import com.victorkoffed.projektandroid.data.db.BrewSample
import com.victorkoffed.projektandroid.data.repository.interfaces.ScaleRepository
import com.victorkoffed.projektandroid.data.repository.interfaces.BeanRepository
import com.victorkoffed.projektandroid.data.repository.interfaces.BrewRepository
import com.victorkoffed.projektandroid.domain.model.BleConnectionState
import com.victorkoffed.projektandroid.domain.model.ScaleMeasurement
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class SaveBrewResult(val brewId: Long?, val beanIdReachedZero: Long? = null)

private data class ReceivedBrewSetup(
    val beanId: Long,
    val doseGrams: Double,
    val methodId: Long,
    val grinderId: Long?,
    val grindSetting: String?,
    val grindSpeedRpm: Double?,
    val brewTempCelsius: Double?
)

private const val TAG = "LiveBrewViewModel_DEBUG"

@HiltViewModel
class LiveBrewViewModel @Inject constructor(
    private val brewRepository: BrewRepository,
    private val beanRepository: BeanRepository,
    private val scaleRepo: ScaleRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private var _setupState: ReceivedBrewSetup? = null

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()
    // ... (resten av states) ...
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

    // --- ÄNDRING 1 av 2: LADE TILL DENNA STATEFLOW ---
    private val _doseGrams = MutableStateFlow(0.0)
    val doseGrams: StateFlow<Double> = _doseGrams.asStateFlow()
    // --- SLUT PÅ ÄNDRING ---

    private var manualTimerJob: Job? = null

    init {
        try {
            _setupState = ReceivedBrewSetup(
                beanId = savedStateHandle.get<Long>("beanId")!!,
                doseGrams = savedStateHandle.get<String>("doseGrams")!!.toDouble(),
                methodId = savedStateHandle.get<Long>("methodId")!!,
                grinderId = savedStateHandle.get<Long>("grinderId").let { if (it == -1L) null else it },
                grindSetting = savedStateHandle.get<String>("grindSetting").let { if (it == "null") null else it },
                grindSpeedRpm = savedStateHandle.get<String>("grindSpeedRpm").let { if (it == "null") null else it?.toDoubleOrNull() },
                brewTempCelsius = savedStateHandle.get<String>("brewTempCelsius").let { if (it == "null") null else it?.toDoubleOrNull() }
            )
            // --- ÄNDRING 2 av 2: SATTE VÄRDET FÖR DOSEN ---
            _doseGrams.value = _setupState!!.doseGrams
            // --- SLUT PÅ ÄNDRING ---
        } catch (e: Exception) {
            Log.e(TAG, "Kunde inte läsa nav arguments för LiveBrewViewModel", e)
            _error.value = "Could not load brew setup. Please go back."
        }

        viewModelScope.launch {
            scaleRepo.observeMeasurements()
                .collect { measurementData ->
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

        viewModelScope.launch {
            scaleRepo.observeConnectionState()
                .collect { state ->
                    val latestMeasurement = scaleRepo.observeMeasurements().value
                    handleConnectionStateChange(state, latestMeasurement)
                }
        }
    }

    private fun handleConnectionStateChange(state: BleConnectionState, latestMeasurement: ScaleMeasurement) {
        if ((state is BleConnectionState.Disconnected || state is BleConnectionState.Error)) {
            if (_isRecording.value && !_isPaused.value) {
                _isRecordingWhileDisconnected.value = true
                _weightAtPause.value = latestMeasurement.weightGrams
                Log.w(TAG, "Recording... DISCONNECTED. Data collection paused, Timer continues.")
            }
            else if (_isRecording.value && _isPaused.value) {
                _isRecordingWhileDisconnected.value = true
                Log.w(TAG, "Manually Paused AND Disconnected.")
            }
        }
        else if (state is BleConnectionState.Connected) {
            if (_isRecordingWhileDisconnected.value) {
                _isRecordingWhileDisconnected.value = false
                Log.d(TAG, "Reconnected while recording. Data collection resumes.")
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
        if (_setupState == null) {
            _error.value = "Setup data is missing."; return
        }

        viewModelScope.launch {
            try {
                _countdown.value = 3; delay(1000L)
                _countdown.value = 2; delay(1000L)
                _countdown.value = 1; delay(1000L)

                scaleRepo.tareScaleAndStartTimer()
                delay(150L)
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

        // Hämta det senast kända paus-värdet (t.ex. 200g)
        val lastKnownWeight = _weightAtPause.value ?: 0f

        // Hämta det nya live-värdet från vågen (t.ex. 0.5g efter återanslutning)
        val liveWeight = measurementData.weightGrams

        // Välj det högsta av de två.
        // Om liveWeight (0.5g) < lastKnownWeight (200g), använd lastKnownWeight.
        // Om liveWeight (201.0g) > lastKnownWeight (200g), använd liveWeight.
        val displayWeight = maxOf(liveWeight, lastKnownWeight)

        // Om vi återansluter och det nya live-värdet nu har "kommit ikapp"
        // (dvs. vågens rådata + offset är nu högre än pausvärdet),
        // måste vi nollställa _weightAtPause så att vi inte fastnar på det gamla värdet.
        if (liveWeight > lastKnownWeight) {
            _weightAtPause.value = null
        }

        // Använd 'displayWeight' istället för 'measurementData.weightGrams'
        val weightGramsDouble = String.format(Locale.US, "%.1f", displayWeight).toDouble()
        val flowRateDouble = measurementData.formatFlowRateToDouble()

        val newSample = BrewSample(
            brewId = 0,
            timeMillis = sampleTimeMillis,
            massGrams = weightGramsDouble, // <-- Använder det korrigerade värdet
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

    suspend fun saveLiveBrew(
        finalSamples: List<BrewSample>,
        finalTimeMillis: Long,
        scaleDeviceName: String?
    ): SaveBrewResult {
        Log.d(TAG, "saveLiveBrew: Start. Time: $finalTimeMillis ms, Samples size: ${finalSamples.size}")

        if (finalSamples.size < 2 || finalTimeMillis <= 0) {
            _error.value = "Not enough data recorded to save."
            Log.e(TAG, "saveLiveBrew: FEL - Otillräcklig data. Samples: ${finalSamples.size}, Time: ${finalTimeMillis}ms")
            return SaveBrewResult(null)
        }

        val setup = _setupState
        if (setup == null) {
            _error.value = "Setup data was missing. Cannot save."
            Log.e(TAG, "saveLiveBrew: FEL - _setupState var null.")
            return SaveBrewResult(null)
        }

        val actualStartTimeMillis = System.currentTimeMillis() - finalTimeMillis
        val scaleInfo = scaleDeviceName?.let { " via $it" } ?: ""
        val newBrew = Brew(
            beanId = setup.beanId,
            doseGrams = setup.doseGrams,
            startedAt = Date(actualStartTimeMillis),
            grinderId = setup.grinderId,
            methodId = setup.methodId,
            grindSetting = setup.grindSetting,
            grindSpeedRpm = setup.grindSpeedRpm,
            brewTempCelsius = setup.brewTempCelsius,
            notes = "Recorded${scaleInfo} on ${
                SimpleDateFormat(
                    "yyyy-MM-dd HH:mm",
                    Locale.getDefault()
                ).format(Date())
            }"
        )
        Log.d(TAG, "saveLiveBrew: Brew-objekt skapat. BeanId: ${setup.beanId}, Dose: ${setup.doseGrams}, MethodId: ${setup.methodId}")

        val savedBrewId: Long? = viewModelScope.async {
            try {
                Log.d(TAG, "saveLiveBrew: Startar repository-transaktion (addBrewWithSamples)...")
                val id = brewRepository.addBrewWithSamples(newBrew, finalSamples) // <-- ÄNDRAD
                Log.d(TAG, "saveLiveBrew: Repository-transaktion LYCKADES. Ny BrewId: $id")
                clearError()
                id
            } catch (e: Exception) {
                Log.e(TAG, "saveLiveBrew: Repository-transaktion MISSLYCKADES under spara!", e)
                _error.value = "Save failed: ${e.message}"
                null
            }
        }.await()

        if (savedBrewId == null) {
            Log.w(TAG, "saveLiveBrew: BrewId är null, sparandet misslyckades på DB-nivå. Returnerar null.")
            return SaveBrewResult(null)
        }

        var beanIdReachedZero: Long? = null
        try {
            val bean = beanRepository.getBeanById(setup.beanId) // <-- ÄNDRAD
            if (bean != null && bean.remainingWeightGrams <= 0.0 && !bean.isArchived) {
                beanIdReachedZero = setup.beanId
            }
        } catch (e: Exception) {
            Log.e(TAG, "saveLiveBrew: Fel vid kontroll av bönans saldo efter sparande", e)
        }

        Log.d(TAG, "saveLiveBrew: Slut. Återvänder BrewId: $savedBrewId, BeanIdReachedZero: $beanIdReachedZero")
        return SaveBrewResult(brewId = savedBrewId, beanIdReachedZero = beanIdReachedZero)
    }

    fun clearError() {
        _error.value = null
    }

    override fun onCleared() {
        super.onCleared()
        manualTimerJob?.cancel()
    }
}