package com.victorkoffed.projektandroid.ui.viewmodel.brew

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.victorkoffed.projektandroid.data.db.Bean
import com.victorkoffed.projektandroid.data.db.Brew
import com.victorkoffed.projektandroid.data.db.BrewMetrics
import com.victorkoffed.projektandroid.data.db.BrewSample
import com.victorkoffed.projektandroid.data.db.Grinder
import com.victorkoffed.projektandroid.data.db.Method
import com.victorkoffed.projektandroid.data.repository.interfaces.BeanRepository
import com.victorkoffed.projektandroid.data.repository.interfaces.BrewRepository
import com.victorkoffed.projektandroid.data.repository.interfaces.GrinderRepository
import com.victorkoffed.projektandroid.data.repository.interfaces.MethodRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BrewDetailState(
    val brew: Brew? = null,
    val bean: Bean? = null,
    val grinder: Grinder? = null,
    val method: Method? = null,
    val samples: List<BrewSample> = emptyList(),
    val metrics: BrewMetrics? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class BrewDetailViewModel @Inject constructor(
    private val brewRepository: BrewRepository,
    private val beanRepository: BeanRepository,
    private val grinderRepository: GrinderRepository,
    private val methodRepository: MethodRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val logTag = "BrewDetailVM"

    private val brewId: Long = savedStateHandle.get<Long>("brewId") ?: throw IllegalArgumentException("brewId not found in SavedStateHandle")

    private val _brewDetailState = MutableStateFlow(BrewDetailState())
    val brewDetailState: StateFlow<BrewDetailState> = _brewDetailState.asStateFlow()

    private val _showArchivePromptOnEntry = MutableStateFlow<Long?>(null)
    val showArchivePromptOnEntry: StateFlow<Long?> = _showArchivePromptOnEntry.asStateFlow()

    var quickEditNotes by mutableStateOf("")
        private set
    var isEditing by mutableStateOf(false)
        private set
    var editSelectedGrinder by mutableStateOf<Grinder?>(null)
        private set
    var editGrindSetting by mutableStateOf("")
        private set
    var editGrindSpeedRpm by mutableStateOf("")
        private set
    var editSelectedMethod by mutableStateOf<Method?>(null)
        private set
    var editBrewTempCelsius by mutableStateOf("")
        private set
    var editNotes by mutableStateOf("")
        private set

    val availableGrinders: StateFlow<List<Grinder>> = grinderRepository.getAllGrinders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val availableMethods: StateFlow<List<Method>> = methodRepository.getAllMethods()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        Log.d(logTag, "ViewModel initierad för brewId: $brewId")
        if (brewId > 0) {
            loadBrewDetails()
            checkForArchivePromptOnEntry()
        } else {
            _brewDetailState.update { it.copy(isLoading = false, error = "Invalid Brew ID provided.") }
        }
    }

    private fun checkForArchivePromptOnEntry() {
        val beanIdToPrompt: Long? = savedStateHandle.get<Long>("beanIdToArchivePrompt")
        if (beanIdToPrompt != null && beanIdToPrompt > 0) {
            Log.d(logTag, "Received beanIdToArchivePrompt via navArg: $beanIdToPrompt")
            _showArchivePromptOnEntry.value = beanIdToPrompt
            savedStateHandle["beanIdToArchivePrompt"] = -1L
        } else {
            Log.d(logTag, "No valid beanIdToArchivePrompt found in navArgs.")
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun loadBrewDetails() {
        Log.d(logTag, "loadBrewDetails anropad...")
        viewModelScope.launch {
            _brewDetailState.update { it.copy(isLoading = true, error = null) }

            brewRepository.observeBrew(brewId)
                .flatMapLatest { brew ->
                    if (brew == null) {
                        flowOf(BrewDetailState(isLoading = false, error = "Brew not found or has been deleted"))
                    } else {
                        val beanFlow = flow { emit(beanRepository.getBeanById(brew.beanId)) }
                        val grinderFlow = brew.grinderId?.let { id -> flow { emit(grinderRepository.getGrinderById(id)) } }
                            ?: flowOf<Grinder?>(null)
                        val methodFlow = brew.methodId?.let { id -> flow { emit(methodRepository.getMethodById(id)) } }
                            ?: flowOf<Method?>(null)
                        val samplesFlow = brewRepository.getSamplesForBrew(brew.id)
                        val metricsFlow = brewRepository.getBrewMetrics(brew.id)

                        combine(beanFlow, grinderFlow, methodFlow, samplesFlow, metricsFlow) { bean, grinder, method, samples, metrics ->
                            BrewDetailState(
                                brew = brew, bean = bean, grinder = grinder, method = method,
                                samples = samples, metrics = metrics, isLoading = false, error = null
                            )
                        }
                    }
                }
                .catch { e ->
                    Log.e(logTag, "Error in loadBrewDetails flow", e)
                    emit(BrewDetailState(isLoading = false, error = "Failed to load brew details: ${e.message}"))
                }
                .collectLatest { state ->
                    _brewDetailState.value = state
                    if (!isEditing && state.brew != null) {
                        val dbNotes = state.brew.notes ?: ""
                        if (quickEditNotes != dbNotes) quickEditNotes = dbNotes
                        resetEditFieldsToCurrentState()
                    }
                }
        }
    }

    // --- Redigeringsfunktioner ---
    fun startEditing() {
        resetEditFieldsToCurrentState()
        isEditing = true
    }

    fun cancelEditing() {
        isEditing = false
        resetEditFieldsToCurrentState()
    }

    fun saveChanges() {
        val currentBrew = _brewDetailState.value.brew ?: return
        viewModelScope.launch {
            try {
                val updatedBrew = currentBrew.copy(
                    grinderId = editSelectedGrinder?.id,
                    grindSetting = editGrindSetting.takeIf { it.isNotBlank() },
                    grindSpeedRpm = editGrindSpeedRpm.toDoubleOrNull(),
                    methodId = editSelectedMethod?.id,
                    brewTempCelsius = editBrewTempCelsius.toDoubleOrNull(),
                    notes = editNotes.takeIf { it.isNotBlank() }
                )
                brewRepository.updateBrew(updatedBrew)
                isEditing = false
            } catch (e: Exception) {
                Log.e(logTag, "Failed to save changes: ${e.message}", e)
                _brewDetailState.update { it.copy(error = "Failed to save changes: ${e.message}") }
            }
        }
    }

    fun onEditGrinderSelected(grinder: Grinder?) { editSelectedGrinder = grinder }
    fun onEditGrindSettingChanged(value: String) { editGrindSetting = value }
    fun onEditGrindSpeedRpmChanged(value: String) { if (value.matches(Regex("^\\d*$"))) editGrindSpeedRpm = value }
    fun onEditMethodSelected(method: Method?) { editSelectedMethod = method }
    fun onEditBrewTempChanged(value: String) { if (value.matches(Regex("^\\d*\\.?\\d*$"))) editBrewTempCelsius = value }
    fun onEditNotesChanged(value: String) { editNotes = value }

    private fun resetEditFieldsToCurrentState() {
        val currentState = _brewDetailState.value
        editSelectedGrinder = currentState.grinder
        editGrindSetting = currentState.brew?.grindSetting ?: ""
        editGrindSpeedRpm = currentState.brew?.grindSpeedRpm?.toInt()?.toString() ?: ""
        editSelectedMethod = currentState.method
        editBrewTempCelsius = currentState.brew?.brewTempCelsius?.toString() ?: ""
        editNotes = currentState.brew?.notes ?: ""
    }

    // --- Snabbredigering av anteckningar ---
    fun onQuickEditNotesChanged(value: String) {
        quickEditNotes = value
    }

    fun saveQuickEditNotes() {
        val currentBrew = _brewDetailState.value.brew ?: return
        viewModelScope.launch {
            try {
                val notesToSave = quickEditNotes.takeIf { it.isNotBlank() }
                if (notesToSave != currentBrew.notes) {
                    val updatedBrew = currentBrew.copy(notes = notesToSave)
                    brewRepository.updateBrew(updatedBrew)
                    quickEditNotes = updatedBrew.notes ?: ""
                }
            } catch (e: Exception) {
                Log.e(logTag, "Failed to save quick notes: ${e.message}", e)
                _brewDetailState.update { it.copy(error = "Failed to save notes: ${e.message}") }
            }
        }
    }

    // --- Bildhantering ---
    fun updateBrewImageUri(uri: String?) {
        Log.d(logTag, "updateBrewImageUri anropad med: $uri")
        val currentBrew = _brewDetailState.value.brew ?: run {
            Log.e(logTag, "updateBrewImageUri: Försökte spara URI, men currentBrew var null!")
            return
        }

        viewModelScope.launch {
            try {
                Log.d(logTag, "Försöker spara URI till databasen för brewId: ${currentBrew.id}")

                val updatedBrew = currentBrew.copy(imageUri = uri)
                brewRepository.updateBrew(updatedBrew)
                _brewDetailState.update { it.copy(brew = updatedBrew) }
            } catch (e: Exception) {
                Log.e(logTag, "Kunde inte spara bild-URI till databasen:", e)
                _brewDetailState.update { it.copy(error = "Kunde inte spara bild: ${e.message}") }
            }
        }
    }

    // --- Radering av bryggning ---
    fun deleteCurrentBrew(onSuccess: () -> Unit) {
        val brewToDelete = _brewDetailState.value.brew
        if (brewToDelete != null) {
            viewModelScope.launch {
                try {
                    brewRepository.deleteBrewAndRestoreStock(brewToDelete)
                    onSuccess()
                } catch (e: Exception) {
                    Log.e(logTag, "Failed to delete brew: ${e.message}", e)
                    _brewDetailState.update { it.copy(error = "Failed to delete brew: ${e.message}") }
                }
            }
        } else {
            _brewDetailState.update { it.copy(error = "Cannot delete, no brew loaded.") }
        }
    }

    // --- Arkiveringsprompt (från LiveBrew) ---
    fun archiveBeanFromPrompt(beanId: Long) {
        viewModelScope.launch {
            try {
                beanRepository.updateBeanArchivedStatus(beanId, true)
                Log.d(logTag, "Bean $beanId archived successfully from prompt.")
                if (beanId == _brewDetailState.value.bean?.id) {
                    _brewDetailState.update { currentState ->
                        if (currentState.bean?.id == beanId && !currentState.bean.isArchived) {
                            currentState.copy(bean = currentState.bean.copy(isArchived = true))
                        } else {
                            currentState
                        }
                    }

                }
            } catch (e: Exception) {
                Log.e(logTag, "Failed to archive bean $beanId from prompt", e)
                _brewDetailState.update { it.copy(error = "Could not archive the bean: ${e.message}") }
            } finally {
                dismissArchivePromptOnEntry()
            }
        }
    }

    fun dismissArchivePromptOnEntry() {
        _showArchivePromptOnEntry.value = null
    }

    fun clearError() {
        _brewDetailState.update { it.copy(error = null) }
    }
}