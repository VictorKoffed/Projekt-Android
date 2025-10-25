package com.victorkoffed.projektandroid.ui.viewmodel.brew

import android.util.Log
import androidx.compose.runtime.getValue // Importera för delegated properties
import androidx.compose.runtime.mutableStateOf // Importera för delegated properties
import androidx.compose.runtime.setValue // Importera för delegated properties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.victorkoffed.projektandroid.data.db.*
import com.victorkoffed.projektandroid.data.repository.CoffeeRepository
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

    // ÄNDRAD: Byter namn för att följa Kotlin Coding Conventions (löser varningen)
    private val logTag = "BrewDetailVM"

    private val _brewDetailState = MutableStateFlow(BrewDetailState())
    val brewDetailState: StateFlow<BrewDetailState> = _brewDetailState.asStateFlow()

    // --- State för snabb redigering av notes (LIVE UI-BINDNING) ---
    var quickEditNotes by mutableStateOf("")
        private set

    // Denna variabel togs bort: private var saveNotesJob: Job? = null


    // --- State för Redigeringsläge ---
    var isEditing by mutableStateOf(false) // Är vi i redigeringsläge?
        private set

    // States för att hålla de redigerade värdena (temporärt)
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

    // Tillgängliga kvarnar/metoder för dropdowns i redigeringsläget
    val availableGrinders: StateFlow<List<Grinder>> = repository.getAllGrinders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val availableMethods: StateFlow<List<Method>> = repository.getAllMethods()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


    init {
        Log.d(logTag, "ViewModel initierad för brewId: $brewId")
        loadBrewDetails()
    }

    private fun loadBrewDetails() {
        Log.d(logTag, "loadBrewDetails anropad...")
        viewModelScope.launch {
            _brewDetailState.update { it.copy(isLoading = true, error = null) }
            try {
                val brew = repository.getBrewById(brewId)
                if (brew == null) {
                    _brewDetailState.update { it.copy(isLoading = false, error = "Brew not found") } // <-- Bättre felhantering
                    return@launch
                }
                Log.d(logTag, "Hittade bryggning: $brew")

                val beanFlow = flow { emit(repository.getBeanById(brew.beanId)) }.onEach { Log.d(logTag, "Hämtade böna: $it") }
                val grinderFlow = brew.grinderId?.let { id -> flow { emit(repository.getGrinderById(id)) } } ?: flowOf<Grinder?>(null).onEach { Log.d(logTag, "Hämtade kvarn: $it") }
                val methodFlow = brew.methodId?.let { id -> flow { emit(repository.getMethodById(id)) } } ?: flowOf<Method?>(null).onEach { Log.d(logTag, "Hämtade metod: $it") }
                val samplesFlow = repository.getSamplesForBrew(brewId).onEach { Log.d(logTag, "Hämtade ${it.size} samples") }
                val metricsFlow = repository.getBrewMetrics(brewId).onEach { Log.d(logTag, "Hämtade metrics: $it") }

                Log.d(logTag, "Startar combine...")
                combine(beanFlow, grinderFlow, methodFlow, samplesFlow, metricsFlow) {
                        bean, grinder, method, samples, metrics ->
                    Log.d(logTag, "Combine emitterar nytt state.")
                    BrewDetailState(
                        brew = brew, bean = bean, grinder = grinder, method = method,
                        samples = samples, metrics = metrics, isLoading = false
                    )
                }.catch { e ->
                    Log.e(logTag, "Error in combine flow", e)
                    _brewDetailState.update { it.copy(isLoading = false, error = "Error loading details: ${e.message}") }
                }.collectLatest { state ->
                    Log.d(logTag, "Uppdaterar state: ...")
                    _brewDetailState.value = state
                    // NYTT: När data laddats, initiera redigeringsfälten
                    if (!isEditing) { // Undvik att skriva över om användaren redan börjat redigera
                        resetEditFieldsToCurrentState()
                    }
                    // NYTT: Sätt initialvärdet för quickEditNotes (För den levande fältet)
                    quickEditNotes = state.brew?.notes ?: ""
                }
            } catch (e: Exception) {
                Log.e(logTag, "Error in loadBrewDetails", e)
                _brewDetailState.update { it.copy(isLoading = false, error = "Failed to load brew: ${e.message}") }
            }
        }
    }

    // --- NYA FUNKTIONER FÖR REDIGERING ---

    /** Startar redigeringsläget och kopierar nuvarande värden till redigerings-states */
    fun startEditing() {
        resetEditFieldsToCurrentState() // Hämta senaste värdena
        isEditing = true
    }

    /** Avbryter redigering och återställer redigeringsfälten */
    fun cancelEditing() {
        isEditing = false
        // Ingen återställning här, låt fälten behålla sina värden tills nästa laddning
    }

    /** Sparar ändringarna från redigeringsfälten till databasen */
    fun saveChanges() {
        val currentBrew = _brewDetailState.value.brew ?: return // Måste ha en bryggning att uppdatera
        viewModelScope.launch {
            try {
                // Skapa ett uppdaterat Brew-objekt med värden från edit-states
                val updatedBrew = currentBrew.copy(
                    grinderId = editSelectedGrinder?.id, // Använd ID från det valda objektet
                    grindSetting = editGrindSetting.takeIf { it.isNotBlank() },
                    grindSpeedRpm = editGrindSpeedRpm.toDoubleOrNull(),
                    methodId = editSelectedMethod?.id, // Använd ID
                    brewTempCelsius = editBrewTempCelsius.toDoubleOrNull(),
                    notes = editNotes.takeIf { it.isNotBlank() }
                    // Fält som INTE ska ändras (beanId, doseGrams, startedAt, imageUri) lämnas orörda
                )
                repository.updateBrew(updatedBrew)
                isEditing = false // Avsluta redigeringsläget
                loadBrewDetails() // Ladda om datan för att visa de sparade ändringarna
            } catch (e: Exception) {
                Log.e(logTag, "Kunde inte spara ändringar: ${e.message}", e)
                _brewDetailState.update { it.copy(error = "Kunde inte spara: ${e.message}") }
                // Låt användaren vara kvar i redigeringsläget för att kunna försöka igen?
            }
        }
    }

    // Funktioner för att uppdatera redigerings-states från UI
    fun onEditGrinderSelected(grinder: Grinder?) { editSelectedGrinder = grinder }
    fun onEditGrindSettingChanged(value: String) { editGrindSetting = value }
    fun onEditGrindSpeedRpmChanged(value: String) { if (value.matches(Regex("^\\d*$"))) editGrindSpeedRpm = value }
    fun onEditMethodSelected(method: Method?) { editSelectedMethod = method }
    fun onEditBrewTempChanged(value: String) { if (value.matches(Regex("^\\d*\\.?\\d*$"))) editBrewTempCelsius = value }
    fun onEditNotesChanged(value: String) { editNotes = value }


    /** Hjälpfunktion för att sätta redigeringsfälten till nuvarande state */
    private fun resetEditFieldsToCurrentState() {
        val currentState = _brewDetailState.value
        editSelectedGrinder = currentState.grinder
        editGrindSetting = currentState.brew?.grindSetting ?: ""
        editGrindSpeedRpm = currentState.brew?.grindSpeedRpm?.toInt()?.toString() ?: "" // Konvertera Double? till Int String
        editSelectedMethod = currentState.method
        editBrewTempCelsius = currentState.brew?.brewTempCelsius?.toString() ?: "" // Konvertera Double? till String
        editNotes = currentState.brew?.notes ?: ""
        // quickEditNotes uppdateras i collectLatest när datan laddas.
    }
    // --- SLUT PÅ NYA FUNKTIONER ---

    // --- NY FUNKTION: För snabb redigering av notes (ADRESSERAR MÅL 5) ---
    // Denna uppdaterar endast det lokala state-värdet
    fun onQuickEditNotesChanged(value: String) {
        quickEditNotes = value
    }

    // NY FUNKTION: Spara anteckningar direkt i databasen (TRIGGERED BY BUTTON)
    fun saveQuickEditNotes() {
        val currentBrew = _brewDetailState.value.brew ?: return
        viewModelScope.launch {
            try {
                val notesToSave = quickEditNotes.takeIf { it.isNotBlank() }

                if (notesToSave != currentBrew.notes) {
                    val updatedBrew = currentBrew.copy(notes = notesToSave)
                    repository.updateBrew(updatedBrew)

                    // FIX: SYNCHRONISERA quickEditNotes EFTER LYCKAD DB-UPPDATERING
                    // Detta säkerställer att UI-fältet behåller det sparade värdet omedelbart.
                    quickEditNotes = updatedBrew.notes ?: ""

                    // Uppdatera Flow State för att UI:t ska reflektera den sparade anteckningen
                    _brewDetailState.update { it.copy(brew = updatedBrew) }
                }
            } catch (e: Exception) {
                Log.e(logTag, "Kunde inte spara anteckningar direkt: ${e.message}", e)
                _brewDetailState.update { it.copy(error = "Kunde inte spara anteckningar: ${e.message}") }
            }
        }
    }
    // --- SLUT NY FUNKTION ---

    // --- NY FUNKTION FÖR BILD ---
    /**
     * Uppdaterar den nuvarande bryggningen med en URI till en bild.
     */
    fun updateBrewImageUri(uri: String?) {
        val currentBrew = _brewDetailState.value.brew ?: return
        viewModelScope.launch {
            try {
                // Skapa en kopia, uppdatera BARA bild-URI:n
                val updatedBrew = currentBrew.copy(imageUri = uri)
                repository.updateBrew(updatedBrew)
                // Ladda om detaljerna för att UI:t ska visa den nya bilden
                loadBrewDetails()
            } catch (e: Exception) {
                Log.e(logTag, "Kunde inte spara bild-URI: ${e.message}", e)
                _brewDetailState.update { it.copy(error = "Kunde inte spara bild: ${e.message}") }
            }
        }
    }
    // --- SLUT PÅ NY FUNKTION ---

    // deleteCurrentBrew (MODIFIERAD)
    fun deleteCurrentBrew(onSuccess: () -> Unit) {
        val brewToDelete = _brewDetailState.value.brew
        if (brewToDelete != null) {
            viewModelScope.launch {
                try {
                    // ANVÄND FUNKTION FÖR ATT RADERA OCH ÅTERSTÄLLA LAGER
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

    /**
     * NY FUNKTION: Nollställer felmeddelandet efter att det har visats.
     */
    fun clearError() {
        _brewDetailState.update { it.copy(error = null) }
    }
}