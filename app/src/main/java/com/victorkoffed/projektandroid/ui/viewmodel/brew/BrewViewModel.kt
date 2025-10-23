package com.victorkoffed.projektandroid.ui.viewmodel.brew

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.victorkoffed.projektandroid.data.db.* // Importera alla db-klasser
import com.victorkoffed.projektandroid.data.repository.CoffeeRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// Data class för att hålla all state för brygg-setup (oförändrad)
data class BrewSetupState(
    val selectedBean: Bean? = null,
    val doseGrams: String = "",
    val selectedGrinder: Grinder? = null,
    val grindSetting: String = "",
    val grindSpeedRpm: String = "",
    val selectedMethod: Method? = null,
    val brewTempCelsius: String = "",
    val notes: String = ""
)

/**
 * ViewModel for managing the Brew setup process AND displaying results.
 */
class BrewViewModel(private val repository: CoffeeRepository) : ViewModel() {

    // --- State för dropdown-listor (oförändrat) ---
    val availableBeans: StateFlow<List<Bean>> = repository.getAllBeans()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val availableGrinders: StateFlow<List<Grinder>> = repository.getAllGrinders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val availableMethods: StateFlow<List<Method>> = repository.getAllMethods()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- State för användarinmatning (oförändrat) ---
    var brewSetupState by mutableStateOf(BrewSetupState())
        private set

    // --- NYTT: State för att visa resultat ---
    // Vi behöver inte exponera ID:t utåt, det hanteras internt via loadBrewResults
    // private val _completedBrewId = MutableStateFlow<Long?>(null)

    private val _completedBrewMetrics = MutableStateFlow<BrewMetrics?>(null)
    val completedBrewMetrics: StateFlow<BrewMetrics?> = _completedBrewMetrics.asStateFlow() // Exponera som StateFlow

    private val _completedBrewSamples = MutableStateFlow<List<BrewSample>>(emptyList())
    val completedBrewSamples: StateFlow<List<BrewSample>> = _completedBrewSamples.asStateFlow() // Exponera som StateFlow
    // --- Slut på nytt ---

    // --- Funktioner för att uppdatera setup state (oförändrade) ---
    fun selectBean(bean: Bean?) { brewSetupState = brewSetupState.copy(selectedBean = bean) }
    fun onDoseChange(dose: String) { if (dose.matches(Regex("^\\d*\\.?\\d*\$"))) brewSetupState = brewSetupState.copy(doseGrams = dose) }
    fun selectGrinder(grinder: Grinder?) { brewSetupState = brewSetupState.copy(selectedGrinder = grinder) }
    fun onGrindSettingChange(setting: String) { brewSetupState = brewSetupState.copy(grindSetting = setting) }
    fun onGrindSpeedChange(rpm: String) { if (rpm.matches(Regex("^\\d*\$"))) brewSetupState = brewSetupState.copy(grindSpeedRpm = rpm) }
    fun selectMethod(method: Method?) { brewSetupState = brewSetupState.copy(selectedMethod = method) }
    fun onBrewTempChange(temp: String) { if (temp.matches(Regex("^\\d*\\.?\\d*\$"))) brewSetupState = brewSetupState.copy(brewTempCelsius = temp) }
    fun onNotesChange(notes: String) { brewSetupState = brewSetupState.copy(notes = notes) }

    fun isSetupValid(): Boolean { return brewSetupState.selectedBean != null && brewSetupState.doseGrams.toDoubleOrNull()?.let { it > 0 } == true && brewSetupState.selectedMethod != null }
    fun getCurrentSetup(): BrewSetupState { return brewSetupState }

    // --- NY FUNKTION: Ladda resultat för en specifik bryggning ---
    /**
     * Anropas (t.ex. från BrewScreen via LaunchedEffect) när en bryggning är klar.
     * Hämtar metrics och samples för det givna ID:t.
     * Om brewId är null, rensas tidigare resultat.
     */
    fun loadBrewResults(brewId: Long?) {
        if (brewId == null) {
            // Rensa gamla resultat
            _completedBrewMetrics.value = null
            _completedBrewSamples.value = emptyList()
            return
        }

        // Hämta metrics för det givna ID:t
        viewModelScope.launch {
            repository.getBrewMetrics(brewId)
                .catch { e -> _completedBrewMetrics.value = null /* Hantera fel? */ } // Nollställ vid fel
                .collectLatest { metrics ->
                    _completedBrewMetrics.value = metrics
                }
        }
        // Hämta samples (grafdata) för det givna ID:t
        viewModelScope.launch {
            repository.getSamplesForBrew(brewId)
                .catch { e -> _completedBrewSamples.value = emptyList() /* Hantera fel? */ } // Töm lista vid fel
                .collectLatest { samples ->
                    _completedBrewSamples.value = samples
                }
        }
    }

    // --- NY FUNKTION: Rensa resultat ---
    /**
     * Rensar de visade resultaten (metrics och samples).
     * Anropas t.ex. när användaren vill starta en helt ny brygg-setup.
     */
    fun clearBrewResults() {
        loadBrewResults(null) // Anropa load med null för att rensa
        // Överväg att även återställa setupState här om du vill att formuläret ska nollställas
        // brewSetupState = BrewSetupState()
    }
}