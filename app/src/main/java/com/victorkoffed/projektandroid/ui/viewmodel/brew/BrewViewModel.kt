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
import dagger.hilt.android.lifecycle.HiltViewModel
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
import javax.inject.Inject

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
@HiltViewModel // <-- NY ANNOTERING
class BrewViewModel @Inject constructor(
    private val repository: CoffeeRepository
) : ViewModel() {

    // --- State för dropdown-listor ---
    val availableBeans: StateFlow<List<Bean>> = repository.getAllBeans()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val availableGrinders: StateFlow<List<Grinder>> = repository.getAllGrinders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val availableMethods: StateFlow<List<Method>> = repository.getAllMethods()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- State för användarinmatning ---
    var brewSetupState by mutableStateOf(BrewSetupState())
        private set

    // --- State för att visa resultat ---
    private val _completedBrewMetrics = MutableStateFlow<BrewMetrics?>(null)
    private val _completedBrewSamples = MutableStateFlow<List<BrewSample>>(emptyList())

    // --- State för att veta om det finns tidigare bryggningar ---
    val hasPreviousBrews: StateFlow<Boolean> = repository.getAllBrews()
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)


    // --- Funktioner för att uppdatera inställningar ---
    fun selectBean(bean: Bean?) { brewSetupState = brewSetupState.copy(selectedBean = bean) }
    fun onDoseChange(dose: String) { if (dose.matches(Regex("^\\d*\\.?\\d*$"))) brewSetupState = brewSetupState.copy(doseGrams = dose) }
    fun selectGrinder(grinder: Grinder?) { brewSetupState = brewSetupState.copy(selectedGrinder = grinder) }
    fun onGrindSettingChange(setting: String) { brewSetupState = brewSetupState.copy(grindSetting = setting) }
    fun onGrindSpeedChange(rpm: String) { if (rpm.matches(Regex("^\\d*$"))) brewSetupState = brewSetupState.copy(grindSpeedRpm = rpm) }
    fun selectMethod(method: Method?) { brewSetupState = brewSetupState.copy(selectedMethod = method) }
    fun onBrewTempChange(temp: String) { if (temp.matches(Regex("^\\d*\\.?\\d*$"))) brewSetupState = brewSetupState.copy(brewTempCelsius = temp) }

    fun isSetupValid(): Boolean { return brewSetupState.selectedBean != null && brewSetupState.doseGrams.toDoubleOrNull()?.let { it > 0 } == true && brewSetupState.selectedMethod != null }
    fun getCurrentSetup(): BrewSetupState { return brewSetupState }

    // --- FUNKTIONER FÖR RESULTAT ---
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


    // --- FUNKTION: Ladda inställningar från senaste bryggningen ---
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

    // --- FUNKTION: Spara bryggning utan grafer ---
    suspend fun saveBrewWithoutSamples(): Long? {
        if (!isSetupValid()) {
            return null
        }

        val currentSetup = brewSetupState

        val newBrew = Brew(
            beanId = currentSetup.selectedBean!!.id,
            doseGrams = currentSetup.doseGrams.toDouble(),
            startedAt = Date(System.currentTimeMillis()),
            grinderId = currentSetup.selectedGrinder?.id,
            methodId = currentSetup.selectedMethod!!.id,
            grindSetting = currentSetup.grindSetting.takeIf { it.isNotBlank() },
            grindSpeedRpm = currentSetup.grindSpeedRpm.toDoubleOrNull(),
            brewTempCelsius = currentSetup.brewTempCelsius.toDoubleOrNull(),
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
}