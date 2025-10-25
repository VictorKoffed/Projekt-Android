// app/src/main/java/com/victorkoffed/projektandroid/ui/viewmodel/bean/BeanDetailViewModel.kt
package com.victorkoffed.projektandroid.ui.viewmodel.bean

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle // <-- NY IMPORT
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.victorkoffed.projektandroid.data.db.Bean
import com.victorkoffed.projektandroid.data.db.Brew
import com.victorkoffed.projektandroid.data.repository.CoffeeRepository
import dagger.hilt.android.lifecycle.HiltViewModel // <-- NY IMPORT
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject // <-- NY IMPORT


data class BeanDetailState(
    val bean: Bean? = null,
    val brews: List<Brew> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

// Annotera med @HiltViewModel
@HiltViewModel
class BeanDetailViewModel @Inject constructor( // <-- @Inject constructor
    private val repository: CoffeeRepository,
    private val savedStateHandle: SavedStateHandle // <-- Injicera SavedStateHandle
) : ViewModel() {

    // Hämta beanId från SavedStateHandle
    private val beanId: Long = savedStateHandle.get<Long>("beanId") ?: throw IllegalArgumentException("beanId not found in SavedStateHandle")

    private val _beanDetailState = MutableStateFlow(BeanDetailState())
    val beanDetailState: StateFlow<BeanDetailState> = _beanDetailState.asStateFlow()

    var isEditing by mutableStateOf(false)
        private set

    var editName by mutableStateOf("")
    var editRoaster by mutableStateOf("")
    var editRoastDateStr by mutableStateOf("")
    var editInitialWeightStr by mutableStateOf("")
    var editRemainingWeightStr by mutableStateOf("")
    var editNotes by mutableStateOf("")

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    init {
        // Kontrollera att beanId faktiskt finns (även om vi redan kastat ovan)
        if (beanId > 0) {
            loadBeanDetails()
        } else {
            _beanDetailState.update { it.copy(isLoading = false, error = "Invalid Bean ID provided.") }
        }
    }

    private fun loadBeanDetails() {
        viewModelScope.launch {
            _beanDetailState.update { it.copy(isLoading = true, error = null) }
            try {
                // Samma logik som tidigare, använder nu 'beanId' från klassens egenskap
                val beanFlow = repository.getAllBeans().map { list -> list.find { it.id == beanId } }
                val brewsFlow = repository.getBrewsForBean(beanId)

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
                    if (!isEditing) {
                        resetEditFieldsToCurrentState()
                    }
                }

            } catch (e: Exception) {
                Log.e("BeanDetailVM", "Error setting up flow", e)
                _beanDetailState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    // --- Resten av funktionerna (startEditing, saveChanges, deleteBean etc.) är oförändrade ---
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
        } catch (_: ParseException) {
            null
        }
    }

    fun saveChanges() {
        val currentBean = _beanDetailState.value.bean ?: return
        val remainingWeight = editRemainingWeightStr.toDoubleOrNull()

        if (editName.isBlank() || remainingWeight == null || remainingWeight < 0) {
            _beanDetailState.update { it.copy(error = "Name and remaining weight are required and must be valid.") }
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
                    onSuccess()
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

    fun clearError() {
        _beanDetailState.update { it.copy(error = null) }
    }
}