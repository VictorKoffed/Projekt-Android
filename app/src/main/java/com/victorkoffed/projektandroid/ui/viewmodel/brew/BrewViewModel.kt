package com.victorkoffed.projektandroid.ui.viewmodel.brew

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.victorkoffed.projektandroid.data.db.* // Importera alla db-klasser
import com.victorkoffed.projektandroid.data.repository.CoffeeRepository
import kotlinx.coroutines.async // <-- NY IMPORT
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Date // <-- NY IMPORT

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

    // ... (alla befintliga states som availableBeans, brewSetupState, etc. är oförändrade) ...

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

    // --- State för att visa resultat (oförändrat) ---
    private val _completedBrewMetrics = MutableStateFlow<BrewMetrics?>(null)
    private val _completedBrewSamples = MutableStateFlow<List<BrewSample>>(emptyList())

    // --- NYTT: State för att veta om tidigare bryggningar finns ---
    val hasPreviousBrews: StateFlow<Boolean> = repository.getAllBrews()
        .map { it.isNotEmpty() } // Kolla om listan inte är tom
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false) // Startvärde false
    // --- SLUT NYTT ---


    // ... (alla befintliga funktioner som selectBean, onDoseChange, loadBrewResults, etc. är oförändrade) ...

    // --- Funktioner för att uppdatera setup state (oförändrade) ---
    fun selectBean(bean: Bean?) { brewSetupState = brewSetupState.copy(selectedBean = bean) }
    fun onDoseChange(dose: String) { if (dose.matches(Regex("^\\d*\\.?\\d*$"))) brewSetupState = brewSetupState.copy(doseGrams = dose) }
    fun selectGrinder(grinder: Grinder?) { brewSetupState = brewSetupState.copy(selectedGrinder = grinder) }
    fun onGrindSettingChange(setting: String) { brewSetupState = brewSetupState.copy(grindSetting = setting) }
    fun onGrindSpeedChange(rpm: String) { if (rpm.matches(Regex("^\\d*$"))) brewSetupState = brewSetupState.copy(grindSpeedRpm = rpm) }
    fun selectMethod(method: Method?) { brewSetupState = brewSetupState.copy(selectedMethod = method) }
    fun onBrewTempChange(temp: String) { if (temp.matches(Regex("^\\d*\\.?\\d*$"))) brewSetupState = brewSetupState.copy(brewTempCelsius = temp) }

    fun isSetupValid(): Boolean { return brewSetupState.selectedBean != null && brewSetupState.doseGrams.toDoubleOrNull()?.let { it > 0 } == true && brewSetupState.selectedMethod != null }
    fun getCurrentSetup(): BrewSetupState { return brewSetupState }

    // --- FUNKTIONER FÖR RESULTAT (Oförändrade) ---
    fun loadBrewResults(brewId: Long?) {
        if (brewId == null) {
            _completedBrewMetrics.value = null
            _completedBrewSamples.value = emptyList()
            return
        }

        viewModelScope.launch {
            repository.getBrewMetrics(brewId)
                .catch { e -> _completedBrewMetrics.value = null }
                .collectLatest { metrics ->
                    _completedBrewMetrics.value = metrics
                }
        }
        viewModelScope.launch {
            repository.getSamplesForBrew(brewId)
                .catch { e -> _completedBrewSamples.value = emptyList() }
                .collectLatest { samples ->
                    _completedBrewSamples.value = samples
                }
        }
    }

    fun clearBrewResults() {
        loadBrewResults(null)
    }


    // --- NY FUNKTION: Ladda inställningar från senaste bryggning ---
    fun loadLatestBrewSettings() {
        viewModelScope.launch {
            val latestBrew = repository.getAllBrews().firstOrNull()?.firstOrNull()

            if (latestBrew != null) {
                val bean = repository.getBeanById(latestBrew.beanId)
                val grinder = latestBrew.grinderId?.let { repository.getGrinderById(it) }
                val method = latestBrew.methodId?.let { repository.getMethodById(it) }

                brewSetupState = brewSetupState.copy(
                    selectedBean = bean,
                    doseGrams = latestBrew.doseGrams.toString(),
                    selectedGrinder = grinder,
                    grindSetting = latestBrew.grindSetting ?: "",
                    grindSpeedRpm = latestBrew.grindSpeedRpm?.toInt()?.toString() ?: "",
                    selectedMethod = method,
                    brewTempCelsius = latestBrew.brewTempCelsius?.toString() ?: "",
                    notes = ""
                )
            }
        }
    }

    // --- NY FUNKTION: Spara bryggning utan graf ---
    /**
     * Sparar den nuvarande setupen som en ny bryggning, men utan några BrewSample-data.
     * Returnerar ID:t för den nya bryggningen.
     */
    suspend fun saveBrewWithoutSamples(): Long? {
        if (!isSetupValid()) {
            return null // Validering misslyckades
        }

        val currentSetup = brewSetupState

        val newBrew = Brew(
            beanId = currentSetup.selectedBean!!.id, // Vi vet att den inte är null pga isSetupValid
            doseGrams = currentSetup.doseGrams.toDouble(), // Vi vet att den är en giltig double
            startedAt = Date(System.currentTimeMillis()), // Sätt starttid till "nu"
            grinderId = currentSetup.selectedGrinder?.id,
            methodId = currentSetup.selectedMethod!!.id, // Vi vet att den inte är null
            grindSetting = currentSetup.grindSetting.takeIf { it.isNotBlank() },
            grindSpeedRpm = currentSetup.grindSpeedRpm.toDoubleOrNull(),
            brewTempCelsius = currentSetup.brewTempCelsius.toDoubleOrNull(),
            notes = currentSetup.notes.takeIf { it.isNotBlank() }
        )

        // Kör i ett async-block för att kunna returnera ID:t
        return viewModelScope.async {
            try {
                // Använd den enkla 'addBrew' istället för 'addBrewWithSamples'
                repository.addBrew(newBrew)
            } catch (_: Exception) {
                // Hantera fel, t.ex. logga
                null
            }
        }.await() // Vänta på att repository-anropet blir klart och returnera ID:t
    }
    // --- SLUT NY FUNKTION ---
}