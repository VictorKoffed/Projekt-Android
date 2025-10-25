package com.victorkoffed.projektandroid.ui.viewmodel.brew

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.victorkoffed.projektandroid.data.db.Bean
import com.victorkoffed.projektandroid.data.db.Brew
import com.victorkoffed.projektandroid.data.db.BrewMetrics
import com.victorkoffed.projektandroid.data.db.BrewSample
import com.victorkoffed.projektandroid.data.db.Grinder
import com.victorkoffed.projektandroid.data.db.Method
import com.victorkoffed.projektandroid.data.repository.CoffeeRepository
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

/**
 * Data class för att hålla all information om en specifik bryggning.
 * Inkluderar relaterade objekt (Bean, Grinder, Method) för en komplett vy.
 */
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
 * ViewModel för att visa OCH redigera detaljerna för en enskild Bryggning.
 */
class BrewDetailViewModel(
    private val repository: CoffeeRepository,
    private val brewId: Long // ID för den bryggning som ska visas
) : ViewModel() {

    private val logTag = "BrewDetailVM"

    private val _brewDetailState = MutableStateFlow(BrewDetailState())
    val brewDetailState: StateFlow<BrewDetailState> = _brewDetailState.asStateFlow()

    // --- State för snabb redigering av notes (LIVE UI-BINDNING utan att vara i 'isEditing') ---
    // Denna variabel binder direkt till textfältet i UI.
    var quickEditNotes by mutableStateOf("")
        private set

    // --- State för Redigeringsläge ---
    // Styr om fullständigt redigeringsformulär visas eller standardvyn
    var isEditing by mutableStateOf(false)
        private set

    // States för temporära redigeringsvärden (används endast när isEditing = true)
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

    // Exponera flöden för tillgängliga kvarnar/metoder för dropdown-listorna i redigeringsläget
    val availableGrinders: StateFlow<List<Grinder>> = repository.getAllGrinders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val availableMethods: StateFlow<List<Method>> = repository.getAllMethods()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        Log.d(logTag, "ViewModel initierad för brewId: $brewId")
        loadBrewDetails()
    }

    /**
     * Bygger det komplexa BrewDetailState från flera reaktiva datakällor (Flows).
     *
     * Använder observeBrew() för att få Brew-objektet som en ström, och
     * flatMapLatest() för att dynamiskt byta till strömmar för relaterade Bean/Grinder/Metrics
     * baserat på Brew-objektets ID:n.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun loadBrewDetails() {
        Log.d(logTag, "loadBrewDetails anropad...")
        viewModelScope.launch {
            _brewDetailState.update { it.copy(isLoading = true, error = null) }

            repository.observeBrew(brewId) // Steg 1: Reaktiv källa för Bryggningen
                .flatMapLatest { brew -> // Steg 2: Byt flödespipeline beroende på Brew
                    if (brew == null) {
                        // Om bryggningen inte hittas
                        flowOf(BrewDetailState(isLoading = false, error = "Brew not found"))
                    } else {
                        // Flöden för relaterad data (hämtas via ID:n i Brew-objektet)
                        // flow { emit(...) } används för att konvertera enkel hämtning till en Flow
                        val beanFlow = flow { emit(repository.getBeanById(brew.beanId)) }
                        val grinderFlow = brew.grinderId?.let { id -> flow { emit(repository.getGrinderById(id)) } }
                            ?: flowOf<Grinder?>(null) // Använd flowOf(null) om ID saknas
                        val methodFlow = brew.methodId?.let { id -> flow { emit(repository.getMethodById(id)) } }
                            ?: flowOf<Method?>(null)
                        val samplesFlow = repository.getSamplesForBrew(brew.id)
                        val metricsFlow = repository.getBrewMetrics(brew.id)

                        // Steg 3: Kombinera alla del-flöden till ett StateFlow
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
                    emit(BrewDetailState(isLoading = false, error = "Failed to load brew: ${e.message}"))
                }
                .collectLatest { state ->
                    _brewDetailState.value = state

                    // Synkronisera lokala states med det nya databasvärdet:
                    if (!isEditing) {
                        // Synkronisera QuickEdit-fältet med DB (för att visa DB-värdet vid start)
                        val dbNotes = state.brew?.notes ?: ""
                        if (quickEditNotes != dbNotes) quickEditNotes = dbNotes
                        // Synkronisera Full Edit-fälten
                        resetEditFieldsToCurrentState()
                    }
                }
        }
    }

    // --- FULL REDIGERING (Styrd av isEditing) ---
    fun startEditing() {
        resetEditFieldsToCurrentState()
        isEditing = true
    }

    fun cancelEditing() {
        isEditing = false
    }

    /**
     * Sparar alla fält i det fullständiga redigeringsläget till databasen.
     */
    fun saveChanges() {
        val currentBrew = _brewDetailState.value.brew ?: return
        viewModelScope.launch {
            try {
                val updatedBrew = currentBrew.copy(
                    grinderId = editSelectedGrinder?.id,
                    grindSetting = editGrindSetting.takeIf { it.isNotBlank() },
                    // Försök parsa RPM och Temperatur till Double
                    grindSpeedRpm = editGrindSpeedRpm.toDoubleOrNull(),
                    methodId = editSelectedMethod?.id,
                    brewTempCelsius = editBrewTempCelsius.toDoubleOrNull(),
                    // Notera: 'editNotes' används här, inte 'quickEditNotes'
                    notes = editNotes.takeIf { it.isNotBlank() }
                )
                repository.updateBrew(updatedBrew)
                isEditing = false
            } catch (e: Exception) {
                Log.e(logTag, "Failed to save changes: ${e.message}", e)
                _brewDetailState.update { it.copy(error = "Failed to save changes: ${e.message}") }
            }
        }
    }

    // Handlers för fullständig redigering (binder UI input till lokala states)
    fun onEditGrinderSelected(grinder: Grinder?) { editSelectedGrinder = grinder }
    fun onEditGrindSettingChanged(value: String) { editGrindSetting = value }
    // Enkel validering för RPM (endast siffror)
    fun onEditGrindSpeedRpmChanged(value: String) { if (value.matches(Regex("^\\d*$"))) editGrindSpeedRpm = value }
    fun onEditMethodSelected(method: Method?) { editSelectedMethod = method }
    // Enkel validering för temperatur (siffror och en decimalpunkt)
    fun onEditBrewTempChanged(value: String) { if (value.matches(Regex("^\\d*\\.?\\d*$"))) editBrewTempCelsius = value }
    fun onEditNotesChanged(value: String) { editNotes = value }

    /**
     * Återställer alla temporära redigeringsfält baserat på det aktuella State-värdet.
     */
    private fun resetEditFieldsToCurrentState() {
        val currentState = _brewDetailState.value
        editSelectedGrinder = currentState.grinder
        editGrindSetting = currentState.brew?.grindSetting ?: ""
        // Konvertera Double till String för UI
        editGrindSpeedRpm = currentState.brew?.grindSpeedRpm?.toInt()?.toString() ?: ""
        editSelectedMethod = currentState.method
        editBrewTempCelsius = currentState.brew?.brewTempCelsius?.toString() ?: ""
        editNotes = currentState.brew?.notes ?: ""
    }

    // --- SNABBREIDGERING AV ANTECKNINGAR ---
    fun onQuickEditNotesChanged(value: String) {
        quickEditNotes = value
    }

    /**
     * Sparar endast anteckningsfältet. Används i visningsläget utan att gå in i full redigering.
     */
    fun saveQuickEditNotes() {
        val currentBrew = _brewDetailState.value.brew ?: return
        viewModelScope.launch {
            try {
                val notesToSave = quickEditNotes.takeIf { it.isNotBlank() }
                // Förhindra spara om anteckningarna inte har ändrats
                if (notesToSave != currentBrew.notes) {
                    val updatedBrew = currentBrew.copy(notes = notesToSave)
                    repository.updateBrew(updatedBrew)
                    // Uppdatera quickEditNotes lokalt för att reflektera det sparade värdet
                    quickEditNotes = updatedBrew.notes ?: ""
                }
            } catch (e: Exception) {
                Log.e(logTag, "Failed to save quick notes: ${e.message}", e)
                _brewDetailState.update { it.copy(error = "Failed to save notes: ${e.message}") }
            }
        }
    }

    // --- Bildhantering ---
    /**
     * Uppdaterar URI:n för den tagna bilden i det aktuella Brew-objektet.
     */
    fun updateBrewImageUri(uri: String?) {
        val currentBrew = _brewDetailState.value.brew ?: return
        viewModelScope.launch {
            try {
                val updatedBrew = currentBrew.copy(imageUri = uri)
                repository.updateBrew(updatedBrew)
                // Flowet tar hand om uppdateringen, men en State.update kan snabba upp UI
                _brewDetailState.update { it.copy(brew = updatedBrew) }
            } catch (e: Exception) {
                Log.e(logTag, "Kunde inte spara bild-URI: ${e.message}", e)
                _brewDetailState.update { it.copy(error = "Kunde inte spara bild: ${e.message}") }
            }
        }
    }

    /**
     * Raderar bryggningen och återställer mängden kaffebönor (stocken).
     *
     * @param onSuccess Callback för navigering när raderingen är klar.
     */
    fun deleteCurrentBrew(onSuccess: () -> Unit) {
        val brewToDelete = _brewDetailState.value.brew
        if (brewToDelete != null) {
            viewModelScope.launch {
                try {
                    // Använder en repository-funktion som hanterar både radering och lagerjustering
                    repository.deleteBrewAndRestoreStock(brewToDelete)
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

    /**
     * Nollställer felmeddelandet efter att det har visats i UI.
     */
    fun clearError() {
        _brewDetailState.update { it.copy(error = null) }
    }
}