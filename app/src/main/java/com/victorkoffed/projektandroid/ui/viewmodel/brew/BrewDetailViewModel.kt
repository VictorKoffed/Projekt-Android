package com.victorkoffed.projektandroid.ui.viewmodel.brew

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.victorkoffed.projektandroid.data.db.*
import com.victorkoffed.projektandroid.data.repository.CoffeeRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// Data class för att hålla all information om en specifik bryggning (oförändrad)
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

/**
 * ViewModel for displaying AND EDITING the details of a single Brew.
 */
class BrewDetailViewModel(
    private val repository: CoffeeRepository,
    private val brewId: Long
) : ViewModel() {

    private val logTag = "BrewDetailVM"

    private val _brewDetailState = MutableStateFlow(BrewDetailState())
    val brewDetailState: StateFlow<BrewDetailState> = _brewDetailState.asStateFlow()

    // --- State för snabb redigering av notes (LIVE UI-BINDNING) ---
    var quickEditNotes by mutableStateOf("")
        private set

    // --- State för Redigeringsläge ---
    var isEditing by mutableStateOf(false)
        private set

    // States för temporära redigeringsvärden
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

    // Tillgängliga kvarnar/metoder för dropdowns
    val availableGrinders: StateFlow<List<Grinder>> = repository.getAllGrinders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val availableMethods: StateFlow<List<Method>> = repository.getAllMethods()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        Log.d(logTag, "ViewModel initierad för brewId: $brewId")
        loadBrewDetails()
    }

    /**
     * ✅ Ny implementation: Bygg state från ett reaktivt Brew-flow.
     * Förhindrar att gamla snapshot-värden skriver över UI efter sparning.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun loadBrewDetails() {
        Log.d(logTag, "loadBrewDetails anropad...")
        viewModelScope.launch {
            _brewDetailState.update { it.copy(isLoading = true, error = null) }

            repository.observeBrew(brewId) // reaktiv källa från Room
                .flatMapLatest { brew ->
                    if (brew == null) {
                        flowOf(BrewDetailState(isLoading = false, error = "Brew not found"))
                    } else {
                        val beanFlow = flow { emit(repository.getBeanById(brew.beanId)) }
                        val grinderFlow = brew.grinderId?.let { id -> flow { emit(repository.getGrinderById(id)) } }
                            ?: flowOf<Grinder?>(null)
                        val methodFlow = brew.methodId?.let { id -> flow { emit(repository.getMethodById(id)) } }
                            ?: flowOf<Method?>(null)
                        val samplesFlow = repository.getSamplesForBrew(brew.id)
                        val metricsFlow = repository.getBrewMetrics(brew.id)

                        combine(beanFlow, grinderFlow, methodFlow, samplesFlow, metricsFlow) { bean, grinder, method, samples, metrics ->
                            BrewDetailState(
                                brew = brew,
                                bean = bean,
                                grinder = grinder,
                                method = method,
                                samples = samples,
                                metrics = metrics,
                                isLoading = false,
                                error = null
                            )
                        }
                    }
                }
                .catch { e ->
                    Log.e(logTag, "Error in loadBrewDetails", e)
                    emit(BrewDetailState(isLoading = false, error = "Failed to load brew: ${e.message}"))
                }
                .collectLatest { state ->
                    _brewDetailState.value = state

                    // Initiera endast när vi inte redigerar och om värdet faktiskt skiljer sig
                    if (!isEditing) {
                        val dbNotes = state.brew?.notes ?: ""
                        if (quickEditNotes != dbNotes) quickEditNotes = dbNotes
                        resetEditFieldsToCurrentState()
                    }
                }
        }
    }

    // --- REDIGERING ---
    fun startEditing() {
        resetEditFieldsToCurrentState()
        isEditing = true
    }

    fun cancelEditing() {
        isEditing = false
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
                repository.updateBrew(updatedBrew)
                isEditing = false
                // Ingen explicit reload behövs – observeBrew pushar ny emission
            } catch (e: Exception) {
                Log.e(logTag, "Kunde inte spara ändringar: ${e.message}", e)
                _brewDetailState.update { it.copy(error = "Kunde inte spara: ${e.message}") }
            }
        }
    }

    // UI → state
    fun onEditGrinderSelected(grinder: Grinder?) { editSelectedGrinder = grinder }
    fun onEditGrindSettingChanged(value: String) { editGrindSetting = value }
    fun onEditGrindSpeedRpmChanged(value: String) { if (value.matches(Regex("^\\d*$"))) editGrindSpeedRpm = value }
    fun onEditMethodSelected(method: Method?) { editSelectedMethod = method }
    fun onEditBrewTempChanged(value: String) { if (value.matches(Regex("^\\d*\\.?\\d*$"))) editBrewTempCelsius = value }
    fun onEditNotesChanged(value: String) { editNotes = value }

    /** Sätter redigeringsfälten till nuvarande state */
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
                    repository.updateBrew(updatedBrew)
                    // Optimistisk uppdatering – UI blir omedelbart rätt
                    _brewDetailState.update { it.copy(brew = updatedBrew) }
                    quickEditNotes = updatedBrew.notes ?: ""
                }
            } catch (e: Exception) {
                Log.e(logTag, "Kunde inte spara anteckningar direkt: ${e.message}", e)
                _brewDetailState.update { it.copy(error = "Kunde inte spara anteckningar: ${e.message}") }
            }
        }
    }

    // --- Bild ---
    fun updateBrewImageUri(uri: String?) {
        val currentBrew = _brewDetailState.value.brew ?: return
        viewModelScope.launch {
            try {
                val updatedBrew = currentBrew.copy(imageUri = uri)
                repository.updateBrew(updatedBrew)
                // observeBrew kommer att emit:a nytt värde; extra set för snabbhet:
                _brewDetailState.update { it.copy(brew = updatedBrew) }
            } catch (e: Exception) {
                Log.e(logTag, "Kunde inte spara bild-URI: ${e.message}", e)
                _brewDetailState.update { it.copy(error = "Kunde inte spara bild: ${e.message}") }
            }
        }
    }

    fun deleteCurrentBrew(onSuccess: () -> Unit) {
        val brewToDelete = _brewDetailState.value.brew
        if (brewToDelete != null) {
            viewModelScope.launch {
                try {
                    repository.deleteBrewAndRestoreStock(brewToDelete)
                    onSuccess()
                } catch (e: Exception) {
                    Log.e(logTag, "Kunde inte radera bryggning: ${e.message}", e)
                    _brewDetailState.update { it.copy(error = "Kunde inte radera: ${e.message}") }
                }
            }
        } else {
            _brewDetailState.update { it.copy(error = "Kan inte radera, ingen bryggning laddad.") }
        }
    }

    fun clearError() {
        _brewDetailState.update { it.copy(error = null) }
    }
}
