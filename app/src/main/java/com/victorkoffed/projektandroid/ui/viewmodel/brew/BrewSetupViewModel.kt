package com.victorkoffed.projektandroid.ui.viewmodel.brew

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.victorkoffed.projektandroid.data.db.Bean
import com.victorkoffed.projektandroid.data.db.Brew
import com.victorkoffed.projektandroid.data.db.Grinder
import com.victorkoffed.projektandroid.data.db.Method
import com.victorkoffed.projektandroid.data.repository.interfaces.BeanRepository
import com.victorkoffed.projektandroid.data.repository.interfaces.BrewRepository
import com.victorkoffed.projektandroid.data.repository.interfaces.GrinderRepository
import com.victorkoffed.projektandroid.data.repository.interfaces.MethodRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

data class NumericInput(
    val value: String = "",
    val error: String? = null
)

data class BrewSetupState(
    val selectedBean: Bean? = null,
    val doseGrams: NumericInput = NumericInput(),
    val targetRatio: NumericInput = NumericInput(), // <-- NYTT FÄLT (från förra svängen)
    val selectedGrinder: Grinder? = null,
    val grindSetting: String = "",
    val grindSpeedRpm: NumericInput = NumericInput(),
    val selectedMethod: Method? = null,
    val brewTempCelsius: NumericInput = NumericInput(),
    val notes: String = ""
)

@HiltViewModel
class BrewSetupViewModel @Inject constructor(
    private val beanRepository: BeanRepository,
    private val grinderRepository: GrinderRepository,
    private val methodRepository: MethodRepository,
    private val brewRepository: BrewRepository
) : ViewModel() {

    private val decimalRegex = Regex("^\\d*\\.?\\d*$")
    private val integerRegex = Regex("^\\d*$")

    val availableBeans: StateFlow<List<Bean>> = beanRepository.getAllBeans()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val availableGrinders: StateFlow<List<Grinder>> = grinderRepository.getAllGrinders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val availableMethods: StateFlow<List<Method>> = methodRepository.getAllMethods()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _brewSetupState = MutableStateFlow(BrewSetupState())
    val brewSetupState: StateFlow<BrewSetupState> = _brewSetupState.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    val hasPreviousBrews: StateFlow<Boolean> = brewRepository.getAllBrews()
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun selectBean(bean: Bean?) {
        _brewSetupState.update { it.copy(selectedBean = bean) }
    }

    fun onDoseChange(dose: String) {
        val isValid = dose.matches(decimalRegex)
        _brewSetupState.update {
            it.copy(
                doseGrams = it.doseGrams.copy(
                    value = dose,
                    error = if (isValid || dose.isBlank()) null else "Måste vara ett giltigt tal (t.ex. 20.5)"
                )
            )
        }
    }

    // --- NY FUNKTION (från förra svängen) ---
    fun onTargetRatioChange(ratio: String) {
        val isValid = ratio.matches(decimalRegex)
        _brewSetupState.update {
            it.copy(
                targetRatio = it.targetRatio.copy(
                    value = ratio,
                    error = if (isValid || ratio.isBlank()) null else "Måste vara ett giltigt tal (t.ex. 17)"
                )
            )
        }
    }
    // --- SLUT NY FUNKTION ---

    fun selectGrinder(grinder: Grinder?) {
        _brewSetupState.update { it.copy(selectedGrinder = grinder) }
    }

    fun onGrindSettingChange(setting: String) {
        _brewSetupState.update { it.copy(grindSetting = setting) }
    }

    fun onGrindSpeedChange(rpm: String) {
        val isValid = rpm.matches(integerRegex)
        _brewSetupState.update {
            it.copy(
                grindSpeedRpm = it.grindSpeedRpm.copy(
                    value = rpm,
                    error = if (isValid || rpm.isBlank()) null else "Måste vara ett heltal"
                )
            )
        }
    }

    fun selectMethod(method: Method?) {
        _brewSetupState.update { it.copy(selectedMethod = method) }
    }

    fun onBrewTempChange(temp: String) {
        val isValid = temp.matches(decimalRegex)
        _brewSetupState.update {
            it.copy(
                brewTempCelsius = it.brewTempCelsius.copy(
                    value = temp,
                    error = if (isValid || temp.isBlank()) null else "Måste vara ett giltigt tal (t.ex. 94.5)"
                )
            )
        }
    }

    fun isSetupValid(): Boolean {
        val currentState = _brewSetupState.value

        val doseValue = currentState.doseGrams.value.toDoubleOrNull()
        val doseValid = doseValue?.let { it > 0 } == true

        val noInputErrors = currentState.doseGrams.error == null &&
                currentState.targetRatio.error == null && // <-- NY KONTROLL (från förra svängen)
                currentState.grindSpeedRpm.error == null &&
                currentState.brewTempCelsius.error == null

        return currentState.selectedBean != null &&
                currentState.selectedMethod != null &&
                doseValid &&
                noInputErrors
    }

    fun getCurrentSetup(): BrewSetupState { return _brewSetupState.value }

    fun clearForm() {
        _brewSetupState.value = BrewSetupState()
    }

    fun loadLatestBrewSettings() {
        viewModelScope.launch {
            val latestBrew = brewRepository.getAllBrews().firstOrNull()?.firstOrNull()

            if (latestBrew != null) {
                val bean = beanRepository.getBeanById(latestBrew.beanId)
                val grinder = latestBrew.grinderId?.let { grinderRepository.getGrinderById(it) }
                val method = latestBrew.methodId?.let { methodRepository.getMethodById(it) }

                _brewSetupState.update {
                    it.copy(
                        selectedBean = bean,
                        doseGrams = NumericInput(latestBrew.doseGrams.toString()),
                        // --- NY ÄNDRING (denna sväng) ---
                        targetRatio = NumericInput(latestBrew.targetRatio?.toString() ?: ""),
                        // --- SLUT NY ÄNDRING ---
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
    }

    suspend fun saveBrewWithoutSamples(): Long? {
        if (!isSetupValid()) {
            return null
        }
        val currentSetup = _brewSetupState.value
        val newBrew = Brew(
            beanId = currentSetup.selectedBean!!.id,
            doseGrams = currentSetup.doseGrams.value.toDouble(),
            startedAt = Date(System.currentTimeMillis()),
            grinderId = currentSetup.selectedGrinder?.id,
            methodId = currentSetup.selectedMethod!!.id,
            grindSetting = currentSetup.grindSetting.takeIf { it.isNotBlank() },
            grindSpeedRpm = currentSetup.grindSpeedRpm.value.toDoubleOrNull(),
            brewTempCelsius = currentSetup.brewTempCelsius.value.toDoubleOrNull(),
            // --- NY ÄNDRING (denna sväng) ---
            targetRatio = currentSetup.targetRatio.value.toDoubleOrNull(),
            // --- SLUT NY ÄNDRING ---
            notes = currentSetup.notes.takeIf { it.isNotBlank() }
        )

        return viewModelScope.async {
            try {
                brewRepository.addBrew(newBrew)
            } catch (e: Exception) {
                _error.value = "Save failed: ${e.message}"
                null
            }
        }.await()
    }

    fun clearError() {
        _error.value = null
    }
}