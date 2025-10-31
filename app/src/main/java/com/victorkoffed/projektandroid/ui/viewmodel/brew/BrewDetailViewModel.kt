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
import com.victorkoffed.projektandroid.data.repository.CoffeeRepository
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

// Annotera med @HiltViewModel
@HiltViewModel
class BrewDetailViewModel @Inject constructor(
    private val repository: CoffeeRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val logTag = "BrewDetailVM"

    // Hämta brewId från SavedStateHandle (obligatoriskt argument)
    private val brewId: Long = savedStateHandle.get<Long>("brewId") ?: throw IllegalArgumentException("brewId not found in SavedStateHandle")

    private val _brewDetailState = MutableStateFlow(BrewDetailState())
    val brewDetailState: StateFlow<BrewDetailState> = _brewDetailState.asStateFlow()

    // State för att visa arkiveringsdialog vid start
    private val _showArchivePromptOnEntry = MutableStateFlow<Long?>(null) // Håller beanId
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

    val availableGrinders: StateFlow<List<Grinder>> = repository.getAllGrinders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val availableMethods: StateFlow<List<Method>> = repository.getAllMethods()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        Log.d(logTag, "ViewModel initierad för brewId: $brewId")
        // Kontrollera att brewId är giltigt
        if (brewId > 0) {
            loadBrewDetails()
            // Kolla om vi ska visa arkiveringsprompt direkt baserat på navArgument
            checkForArchivePromptOnEntry()
        } else {
            _brewDetailState.update { it.copy(isLoading = false, error = "Invalid Brew ID provided.") }
        }
    }

    // Funktion för att kolla SavedStateHandle för arkiveringsargumentet
    private fun checkForArchivePromptOnEntry() {
        // Använd nyckeln från navArgument ("beanIdToArchivePrompt")
        val beanIdToPrompt: Long? = savedStateHandle.get<Long>("beanIdToArchivePrompt")
        // Kontrollera om värdet är giltigt (inte null och inte default -1L)
        if (beanIdToPrompt != null && beanIdToPrompt > 0) {
            Log.d(logTag, "Received beanIdToArchivePrompt via navArg: $beanIdToPrompt")
            _showArchivePromptOnEntry.value = beanIdToPrompt
            // Rensa argumentet från SavedStateHandle för att undvika att prompten visas igen vid rotation etc.
            // SavedStateHandle är mutable.
            savedStateHandle["beanIdToArchivePrompt"] = -1L // Sätt tillbaka till default
        } else {
            Log.d(logTag, "No valid beanIdToArchivePrompt found in navArgs.")
        }
    }


    @OptIn(ExperimentalCoroutinesApi::class)
    private fun loadBrewDetails() {
        Log.d(logTag, "loadBrewDetails anropad...")
        viewModelScope.launch {
            _brewDetailState.update { it.copy(isLoading = true, error = null) }

            // Använd observeBrew för att få reaktiva uppdateringar
            repository.observeBrew(brewId)
                .flatMapLatest { brew ->
                    if (brew == null) {
                        // Om bryggningen inte finns (t.ex. raderad), sätt ett felmeddelande
                        flowOf(BrewDetailState(isLoading = false, error = "Brew not found or has been deleted"))
                    } else {
                        // Flöden för att hämta relaterade data (Bean, Grinder, Method, Samples, Metrics)
                        // Använder flowOf för att hantera null-värden elegant
                        val beanFlow = flow { emit(repository.getBeanById(brew.beanId)) } // Hämtar bönan oavsett arkivstatus
                        val grinderFlow = brew.grinderId?.let { id -> flow { emit(repository.getGrinderById(id)) } }
                            ?: flowOf<Grinder?>(null)
                        val methodFlow = brew.methodId?.let { id -> flow { emit(repository.getMethodById(id)) } }
                            ?: flowOf<Method?>(null)
                        val samplesFlow = repository.getSamplesForBrew(brew.id)
                        val metricsFlow = repository.getBrewMetrics(brew.id)

                        // Kombinera alla flöden till ett enda state-objekt
                        combine(beanFlow, grinderFlow, methodFlow, samplesFlow, metricsFlow) { bean, grinder, method, samples, metrics ->
                            BrewDetailState(
                                brew = brew, bean = bean, grinder = grinder, method = method,
                                samples = samples, metrics = metrics, isLoading = false, error = null
                            )
                        }
                    }
                }
                .catch { e ->
                    // Hantera fel under datainhämtningen
                    Log.e(logTag, "Error in loadBrewDetails flow", e)
                    emit(BrewDetailState(isLoading = false, error = "Failed to load brew details: ${e.message}"))
                }
                .collectLatest { state ->
                    // Uppdatera ViewModel-state
                    _brewDetailState.value = state
                    // Uppdatera quickEditNotes och redigeringsfälten om vi inte redigerar
                    if (!isEditing && state.brew != null) {
                        val dbNotes = state.brew.notes ?: ""
                        if (quickEditNotes != dbNotes) quickEditNotes = dbNotes
                        resetEditFieldsToCurrentState() // Säkerställ att redigeringsfälten matchar det laddade state
                    }
                }
        }
    }

    // --- Redigeringsfunktioner ---
    fun startEditing() {
        resetEditFieldsToCurrentState() // Se till att fälten är uppdaterade innan redigering
        isEditing = true
    }

    fun cancelEditing() {
        isEditing = false
        // Återställ eventuella osparade ändringar i fälten
        resetEditFieldsToCurrentState()
    }

    fun saveChanges() {
        val currentBrew = _brewDetailState.value.brew ?: return
        viewModelScope.launch {
            try {
                // Skapa en uppdaterad Brew-entitet med värden från redigeringsfälten
                val updatedBrew = currentBrew.copy(
                    grinderId = editSelectedGrinder?.id,
                    grindSetting = editGrindSetting.takeIf { it.isNotBlank() },
                    grindSpeedRpm = editGrindSpeedRpm.toDoubleOrNull(),
                    methodId = editSelectedMethod?.id,
                    brewTempCelsius = editBrewTempCelsius.toDoubleOrNull(),
                    notes = editNotes.takeIf { it.isNotBlank() } // Spara bara icke-tomma anteckningar
                )
                // Spara ändringarna via repositoryt
                repository.updateBrew(updatedBrew)
                isEditing = false // Avsluta redigeringsläget
            } catch (e: Exception) {
                // Hantera sparfel
                Log.e(logTag, "Failed to save changes: ${e.message}", e)
                _brewDetailState.update { it.copy(error = "Failed to save changes: ${e.message}") }
            }
        }
    }

    // Funktioner för att uppdatera redigeringsfälten från UI
    fun onEditGrinderSelected(grinder: Grinder?) { editSelectedGrinder = grinder }
    fun onEditGrindSettingChanged(value: String) { editGrindSetting = value }
    fun onEditGrindSpeedRpmChanged(value: String) { if (value.matches(Regex("^\\d*$"))) editGrindSpeedRpm = value }
    fun onEditMethodSelected(method: Method?) { editSelectedMethod = method }
    fun onEditBrewTempChanged(value: String) { if (value.matches(Regex("^\\d*\\.?\\d*$"))) editBrewTempCelsius = value }
    fun onEditNotesChanged(value: String) { editNotes = value }

    // Återställer redigeringsfälten till det aktuella state (används vid start/avbryt redigering)
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
                // Spara endast om anteckningarna faktiskt har ändrats
                if (notesToSave != currentBrew.notes) {
                    val updatedBrew = currentBrew.copy(notes = notesToSave)
                    repository.updateBrew(updatedBrew)
                    // Uppdatera quickEditNotes igen för att reflektera det sparade värdet (kan vara null)
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
        val currentBrew = _brewDetailState.value.brew ?: return
        viewModelScope.launch {
            try {
                val updatedBrew = currentBrew.copy(imageUri = uri)
                repository.updateBrew(updatedBrew)
                // Uppdatera lokalt state direkt för snabbare UI-respons
                _brewDetailState.update { it.copy(brew = updatedBrew) }
            } catch (e: Exception) {
                Log.e(logTag, "Kunde inte spara bild-URI: ${e.message}", e)
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
                    // Använder transaktionen som återställer lagersaldot
                    repository.deleteBrewAndRestoreStock(brewToDelete)
                    onSuccess() // Kör callback för att t.ex. navigera tillbaka
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

    /**
     * Funktion för att arkivera bönan (anropas från UI när prompten visas).
     * Denna funktion anropar direkt repositoryt för att uppdatera arkivstatus.
     */
    fun archiveBeanFromPrompt(beanId: Long) {
        viewModelScope.launch {
            try {
                repository.updateBeanArchivedStatus(beanId, true)
                Log.d(logTag, "Bean $beanId archived successfully from prompt.")
                // Om den arkiverade bönan är den som visas på skärmen (vilket den borde vara),
                // ladda om detaljerna för att reflektera ändringen.
                if (beanId == _brewDetailState.value.bean?.id) {
                    // VI BEHÖVER INTE LADDA OM HELA - observeBean uppdaterar automatiskt
                    // loadBrewDetails() // Ladda om för att visa uppdaterad bönstatus
                    // Istället, bara uppdatera lokalt state om det inte redan reflekterats
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
                dismissArchivePromptOnEntry() // Dölj prompten oavsett resultat
            }
        }
    }

    /** Funktion för att avvisa arkiveringsprompten. */
    fun dismissArchivePromptOnEntry() {
        _showArchivePromptOnEntry.value = null
    }


    // --- Felhantering ---
    fun clearError() {
        _brewDetailState.update { it.copy(error = null) }
    }
}