package com.victorkoffed.projektandroid.ui.viewmodel.bean

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.victorkoffed.projektandroid.data.db.Bean
import com.victorkoffed.projektandroid.data.db.Brew
import com.victorkoffed.projektandroid.data.repository.CoffeeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import kotlinx.coroutines.flow.map
import java.util.Locale

// Data class för att hålla all state för detaljskärmen
data class BeanDetailState(
    val bean: Bean? = null,
    val brews: List<Brew> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

class BeanDetailViewModel(
    private val repository: CoffeeRepository,
    private val beanId: Long
) : ViewModel() {

    private val _beanDetailState = MutableStateFlow(BeanDetailState())
    val beanDetailState: StateFlow<BeanDetailState> = _beanDetailState.asStateFlow()

    // State för redigeringsläge
    var isEditing by mutableStateOf(false)
        private set

    // States för att hålla redigerade värden (för dialogrutan)
    var editName by mutableStateOf("")
    var editRoaster by mutableStateOf("")
    var editRoastDateStr by mutableStateOf("")
    var editInitialWeightStr by mutableStateOf("")
    var editRemainingWeightStr by mutableStateOf("")
    var editNotes by mutableStateOf("")

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    init {
        loadBeanDetails()
    }

    private fun loadBeanDetails() {
        viewModelScope.launch {
            _beanDetailState.update { it.copy(isLoading = true, error = null) }
            try {
                // Skapa två separata flöden: ett för bönan, ett för bryggningarna
                val beanFlow = repository.getAllBeans().map { list -> list.find { it.id == beanId } }
                val brewsFlow = repository.getBrewsForBean(beanId)

                // Kombinera dem
                combine(beanFlow, brewsFlow) { bean, brews ->
                    BeanDetailState(
                        bean = bean,
                        brews = brews,
                        isLoading = false
                    )
                }.catch { e ->
                    Log.e("BeanDetailVM", "Error loading details", e)
                    _beanDetailState.update { it.copy(isLoading = false, error = e.message) }
                }.collectLatest { state ->
                    _beanDetailState.value = state
                    // Initiera redigeringsfälten när datan laddats
                    if (!isEditing) {
                        resetEditFieldsToCurrentState()
                    }
                }

            } catch (e: Exception) {
                Log.e("BeanDetailVM", "Error loading details", e)
                _beanDetailState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    // --- Redigeringslogik (Flyttad från BeanViewModel/BeanScreen) ---

    fun startEditing() {
        resetEditFieldsToCurrentState()
        isEditing = true
    }

    fun cancelEditing() {
        isEditing = false
    }

    private fun parseDateString(dateString: String?): Date? {
        if (dateString.isNullOrBlank()) return null
        return try {
            dateFormat.parse(dateString)
        } catch (e: ParseException) {
            null
        }
    }

    fun saveChanges() {
        val currentBean = _beanDetailState.value.bean ?: return
        val remainingWeight = editRemainingWeightStr.toDoubleOrNull()
        if (editName.isBlank() || remainingWeight == null || remainingWeight < 0) {
            // Visa fel? (Bör hanteras av knappens enabled-state)
            return
        }

        val roastDate = parseDateString(editRoastDateStr)
        val initialWeight = editInitialWeightStr.toDoubleOrNull()

        val updatedBean = currentBean.copy(
            name = editName,
            roaster = editRoaster.takeIf { it.isNotBlank() },
            roastDate = roastDate,
            initialWeightGrams = initialWeight,
            remainingWeightGrams = remainingWeight,
            notes = editNotes.takeIf { it.isNotBlank() }
        )

        viewModelScope.launch {
            try {
                repository.updateBean(updatedBean)
                isEditing = false
                // Ingen manuell reload behövs, Flow uppdaterar automatiskt
            } catch (e: Exception) {
                _beanDetailState.update { it.copy(error = "Failed to save: ${e.message}") }
            }
        }
    }

    fun deleteBean(onSuccess: () -> Unit) {
        val beanToDelete = _beanDetailState.value.bean
        if (beanToDelete != null) {
            viewModelScope.launch {
                try {
                    repository.deleteBean(beanToDelete)
                    onSuccess() // Navigera tillbaka
                } catch (e: Exception) {
                    _beanDetailState.update { it.copy(error = "Failed to delete: ${e.message}") }
                }
            }
        }
    }

    private fun resetEditFieldsToCurrentState() {
        val bean = _beanDetailState.value.bean
        editName = bean?.name ?: ""
        editRoaster = bean?.roaster ?: ""
        editRoastDateStr = bean?.roastDate?.let { dateFormat.format(it) } ?: ""
        editInitialWeightStr = bean?.initialWeightGrams?.toString() ?: ""
        editRemainingWeightStr = bean?.remainingWeightGrams?.toString() ?: ""
        editNotes = bean?.notes ?: ""
    }
}