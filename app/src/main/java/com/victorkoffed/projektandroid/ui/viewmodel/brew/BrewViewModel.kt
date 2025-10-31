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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** Representerar inmatningsvärde och tillhörande fel. */
data class NumericInput(
    val value: String = "",
    val error: String? = null
)

/** Representerar all användarinmatning för en ny bryggning innan den sparas. */
data class BrewSetupState(
    val selectedBean: Bean? = null,
    val doseGrams: NumericInput = NumericInput(),
    val selectedGrinder: Grinder? = null,
    val grindSetting: String = "",
    val grindSpeedRpm: NumericInput = NumericInput(),
    val selectedMethod: Method? = null,
    val brewTempCelsius: NumericInput = NumericInput(),
    val notes: String = ""
)

/** Data class som returneras efter att en bryggning har sparats. */
data class SaveBrewResult(val brewId: Long?, val beanIdReachedZero: Long? = null)

@HiltViewModel
class BrewViewModel @Inject constructor(
    private val repository: CoffeeRepository
) : ViewModel() {

    private val decimalRegex = Regex("^\\d*\\.?\\d*$")
    private val integerRegex = Regex("^\\d*$")

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

    // --- State för felhantering vid sparande ---
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // --- State för att visa resultat ---
    private val _completedBrewMetrics = MutableStateFlow<BrewMetrics?>(null)
    private val _completedBrewSamples = MutableStateFlow<List<BrewSample>>(emptyList())

    // --- State för att veta om det finns tidigare bryggningar ---
    val hasPreviousBrews: StateFlow<Boolean> = repository.getAllBrews()
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)


    // --- Funktioner för att uppdatera inställningar ---
    fun selectBean(bean: Bean?) { brewSetupState = brewSetupState.copy(selectedBean = bean) }

    fun onDoseChange(dose: String) {
        val isValid = dose.matches(decimalRegex)
        brewSetupState = brewSetupState.copy(
            doseGrams = brewSetupState.doseGrams.copy(
                value = dose,
                error = if (isValid || dose.isBlank()) null else "Måste vara ett giltigt tal (t.ex. 20.5)"
            )
        )
    }

    fun selectGrinder(grinder: Grinder?) { brewSetupState = brewSetupState.copy(selectedGrinder = grinder) }
    fun onGrindSettingChange(setting: String) { brewSetupState = brewSetupState.copy(grindSetting = setting) }

    fun onGrindSpeedChange(rpm: String) {
        val isValid = rpm.matches(integerRegex)
        brewSetupState = brewSetupState.copy(
            grindSpeedRpm = brewSetupState.grindSpeedRpm.copy(
                value = rpm,
                error = if (isValid || rpm.isBlank()) null else "Måste vara ett heltal"
            )
        )
    }

    fun selectMethod(method: Method?) { brewSetupState = brewSetupState.copy(selectedMethod = method) }

    fun onBrewTempChange(temp: String) {
        val isValid = temp.matches(decimalRegex)
        brewSetupState = brewSetupState.copy(
            brewTempCelsius = brewSetupState.brewTempCelsius.copy(
                value = temp,
                error = if (isValid || temp.isBlank()) null else "Måste vara ett giltigt tal (t.ex. 94.5)"
            )
        )
    }

    fun isSetupValid(): Boolean {
        val doseValue = brewSetupState.doseGrams.value.toDoubleOrNull()
        val doseValid = doseValue?.let { it > 0 } == true

        val noInputErrors = brewSetupState.doseGrams.error == null &&
                brewSetupState.grindSpeedRpm.error == null &&
                brewSetupState.brewTempCelsius.error == null

        return brewSetupState.selectedBean != null &&
                brewSetupState.selectedMethod != null &&
                doseValid &&
                noInputErrors
    }

    fun getCurrentSetup(): BrewSetupState { return brewSetupState }

    // --- Funktioner för resultat ---
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


    // --- Funktion: Ladda inställningar från senaste bryggningen ---
    fun loadLatestBrewSettings() {
        viewModelScope.launch {
            val latestBrew = repository.getAllBrews().firstOrNull()?.firstOrNull()

            if (latestBrew != null) {
                val bean = repository.getBeanById(latestBrew.beanId)
                val grinder = latestBrew.grinderId?.let { repository.getGrinderById(it) }
                val method = latestBrew.methodId?.let { repository.getMethodById(it) }

                brewSetupState = brewSetupState.copy(
                    selectedBean = bean,
                    doseGrams = NumericInput(latestBrew.doseGrams.toString()),
                    selectedGrinder = grinder,
                    grindSetting = latestBrew.grindSetting ?: "",
                    grindSpeedRpm = NumericInput(latestBrew.grindSpeedRpm?.toInt()?.toString() ?: ""),
                    selectedMethod = method,
                    brewTempCelsius = NumericInput(latestBrew.brewTempCelsius?.toString() ?: ""),
                    notes = ""
                )
            }
        }
    }

    // --- Funktion: Spara bryggning utan grafer ---
    suspend fun saveBrewWithoutSamples(): Long? {
        if (!isSetupValid()) {
            return null
        }

        val currentSetup = brewSetupState

        val newBrew = Brew(
            beanId = currentSetup.selectedBean!!.id,
            doseGrams = currentSetup.doseGrams.value.toDouble(),
            startedAt = Date(System.currentTimeMillis()),
            grinderId = currentSetup.selectedGrinder?.id,
            methodId = currentSetup.selectedMethod!!.id,
            grindSetting = currentSetup.grindSetting.takeIf { it.isNotBlank() },
            grindSpeedRpm = currentSetup.grindSpeedRpm.value.toDoubleOrNull(),
            brewTempCelsius = currentSetup.brewTempCelsius.value.toDoubleOrNull(),
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

    /**
     * Sparar en live-bryggning.
     */
    suspend fun saveLiveBrew(
        setupState: BrewSetupState,
        finalSamples: List<BrewSample>,
        finalTimeMillis: Long,
        scaleDeviceName: String?
    ): SaveBrewResult {
        if (finalSamples.size < 2 || finalTimeMillis <= 0) {
            _error.value = "Not enough data recorded to save."
            return SaveBrewResult(null)
        }

        // Validera Setup (använder nu .value för att konvertera)
        val beanId = setupState.selectedBean?.id
        val doseGrams = setupState.doseGrams.value.toDoubleOrNull()
        if (beanId == null || doseGrams == null) {
            _error.value = "Missing bean/dose in setup."
            return SaveBrewResult(null)
        }

        // Skapa Brew-objekt
        val actualStartTimeMillis = System.currentTimeMillis() - finalTimeMillis
        val scaleInfo = scaleDeviceName?.let { " via $it" } ?: ""
        val newBrew = Brew(
            beanId = beanId,
            doseGrams = doseGrams,
            startedAt = Date(actualStartTimeMillis),
            grinderId = setupState.selectedGrinder?.id,
            methodId = setupState.selectedMethod?.id,
            grindSetting = setupState.grindSetting.takeIf { it.isNotBlank() },
            grindSpeedRpm = setupState.grindSpeedRpm.value.toDoubleOrNull(),
            brewTempCelsius = setupState.brewTempCelsius.value.toDoubleOrNull(),
            notes = "Recorded${scaleInfo} on ${
                SimpleDateFormat(
                    "yyyy-MM-dd HH:mm",
                    Locale.getDefault()
                ).format(Date())
            }"
        )

        // Spara Brew och Samples (med transaktion)
        val savedBrewId: Long? = viewModelScope.async {
            try {
                val id = repository.addBrewWithSamples(newBrew, finalSamples)
                clearError()
                id
            } catch (e: Exception) {
                _error.value = "Save failed: ${e.message}"
                null
            }
        }.await()

        var beanIdReachedZero: Long? = null
        if (savedBrewId != null) {
            // Kontrollera om lagersaldot nu är noll efter att dosen dragits bort
            try {
                val bean = repository.getBeanById(beanId)
                if (bean != null && bean.remainingWeightGrams <= 0.0 && !bean.isArchived) {
                    beanIdReachedZero = beanId
                }
            } catch (_: Exception) {
                // Log.e(TAG, "Check bean stock failed after save", e) // Kan inte logga TAG här
            }
        }
        return SaveBrewResult(brewId = savedBrewId, beanIdReachedZero = beanIdReachedZero)
    }

    fun clearError() {
        _error.value = null
    }
}