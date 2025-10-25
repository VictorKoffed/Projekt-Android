package com.victorkoffed.projektandroid.ui.viewmodel.brew

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
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Date

/**
 * Representerar all användarinmatning för en ny bryggning innan den sparas.
 */
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
 * ViewModel för att hantera inställningar inför en bryggning och visa eventuella resultat.
 */
class BrewViewModel(private val repository: CoffeeRepository) : ViewModel() {

    // --- State för dropdown-listor ---
    // Listor med tillgängliga entiteter (bönor, kvarnar, metoder) från databasen.
    val availableBeans: StateFlow<List<Bean>> = repository.getAllBeans()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val availableGrinders: StateFlow<List<Grinder>> = repository.getAllGrinders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val availableMethods: StateFlow<List<Method>> = repository.getAllMethods()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- State för användarinmatning ---
    // Håller alla aktuella inställningar för den pågående bryggningen.
    var brewSetupState by mutableStateOf(BrewSetupState())
        private set

    // --- State för att visa resultat ---
    // Metriken (t.ex. total tid, maxflöde) för en slutförd bryggning.
    private val _completedBrewMetrics = MutableStateFlow<BrewMetrics?>(null)
    // De enskilda mätpunkterna (tid, flöde, temperatur) för en slutförd bryggning.
    private val _completedBrewSamples = MutableStateFlow<List<BrewSample>>(emptyList())

    // --- State för att veta om det finns tidigare bryggningar ---
    // Används för att bestämma om "Ladda senaste inställningar"-knappen ska visas.
    val hasPreviousBrews: StateFlow<Boolean> = repository.getAllBrews()
        .map { it.isNotEmpty() } // Kontrollera om listan med bryggningar inte är tom.
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)


    // --- Funktioner för att uppdatera inställningar ---
    fun selectBean(bean: Bean?) { brewSetupState = brewSetupState.copy(selectedBean = bean) }
    // Validerar att input är ett giltigt tal (tomt, heltal eller decimaltal).
    fun onDoseChange(dose: String) { if (dose.matches(Regex("^\\d*\\.?\\d*$"))) brewSetupState = brewSetupState.copy(doseGrams = dose) }
    fun selectGrinder(grinder: Grinder?) { brewSetupState = brewSetupState.copy(selectedGrinder = grinder) }
    fun onGrindSettingChange(setting: String) { brewSetupState = brewSetupState.copy(grindSetting = setting) }
    // Validerar att input är ett heltal.
    fun onGrindSpeedChange(rpm: String) { if (rpm.matches(Regex("^\\d*$"))) brewSetupState = brewSetupState.copy(grindSpeedRpm = rpm) }
    fun selectMethod(method: Method?) { brewSetupState = brewSetupState.copy(selectedMethod = method) }
    // Validerar att input är ett giltigt tal (tomt, heltal eller decimaltal).
    fun onBrewTempChange(temp: String) { if (temp.matches(Regex("^\\d*\\.?\\d*$"))) brewSetupState = brewSetupState.copy(brewTempCelsius = temp) }

    // Kontrollerar att obligatoriska fält (böna, dos, metod) är ifyllda.
    fun isSetupValid(): Boolean { return brewSetupState.selectedBean != null && brewSetupState.doseGrams.toDoubleOrNull()?.let { it > 0 } == true && brewSetupState.selectedMethod != null }
    fun getCurrentSetup(): BrewSetupState { return brewSetupState }

    // --- FUNKTIONER FÖR RESULTAT ---
    // Laddar mätdata och mätpunkter för en specifik sparad bryggning.
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

    // Nollställer visningen av bryggresultat.
    fun clearBrewResults() {
        loadBrewResults(null)
    }


    // --- FUNKTION: Ladda inställningar från senaste bryggningen ---
    // Hämtar inställningarna från den senast sparade bryggningen och fyller i formuläret.
    fun loadLatestBrewSettings() {
        viewModelScope.launch {
            // firstOrNull().firstOrNull() hämtar den senaste bryggningen från StateFlow.
            val latestBrew = repository.getAllBrews().firstOrNull()?.firstOrNull()

            if (latestBrew != null) {
                // Måste hämta de fullständiga objekten (Bean, Grinder, Method) baserat på ID.
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
                    notes = "" // Anteckningar rensas för den nya bryggningen.
                )
            }
        }
    }

    // --- FUNKTION: Spara bryggning utan grafer ---
    /**
     * Sparar de nuvarande inställningarna som en ny Brew, men utan några BrewSample-mätpunkter.
     * Denna funktion används när användaren inte spelar in mätdata (graf).
     *
     * @return ID:t för den nya bryggningen, eller null om sparandet misslyckades.
     */
    suspend fun saveBrewWithoutSamples(): Long? {
        if (!isSetupValid()) {
            return null // Validering misslyckades
        }

        val currentSetup = brewSetupState

        val newBrew = Brew(
            beanId = currentSetup.selectedBean!!.id, // Validerad som icke-null
            doseGrams = currentSetup.doseGrams.toDouble(), // Validerad som giltig double
            startedAt = Date(System.currentTimeMillis()), // Sätter starttiden till "nu"
            grinderId = currentSetup.selectedGrinder?.id,
            methodId = currentSetup.selectedMethod!!.id, // Validerad som icke-null
            grindSetting = currentSetup.grindSetting.takeIf { it.isNotBlank() },
            grindSpeedRpm = currentSetup.grindSpeedRpm.toDoubleOrNull(),
            brewTempCelsius = currentSetup.brewTempCelsius.toDoubleOrNull(),
            notes = currentSetup.notes.takeIf { it.isNotBlank() }
        )

        // Kör i ett async-block för att kunna returnera ID:t synkront.
        return viewModelScope.async {
            try {
                repository.addBrew(newBrew)
            } catch (_: Exception) {
                // Hantera fel, t.ex. logga
                null
            }
        }.await() // Väntar på att repository-anropet ska slutföras och returnerar ID:t.
    }
}