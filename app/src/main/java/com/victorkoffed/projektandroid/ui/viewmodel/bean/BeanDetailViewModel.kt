package com.victorkoffed.projektandroid.ui.viewmodel.bean

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.victorkoffed.projektandroid.data.db.Bean
import com.victorkoffed.projektandroid.data.db.Brew
import com.victorkoffed.projektandroid.data.repository.interfaces.BeanRepository
import com.victorkoffed.projektandroid.data.repository.interfaces.BrewRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
import java.util.Locale
import javax.inject.Inject

data class BeanDetailState(
    val bean: Bean? = null,
    val brews: List<Brew> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class BeanDetailViewModel @Inject constructor(
    private val beanRepository: BeanRepository,
    private val brewRepository: BrewRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val beanId: Long = savedStateHandle.get<Long>("beanId") ?: throw IllegalArgumentException("beanId not found in SavedStateHandle")

    private val _beanDetailState = MutableStateFlow(BeanDetailState())
    val beanDetailState: StateFlow<BeanDetailState> = _beanDetailState.asStateFlow()

    private val _showArchivePromptAfterSave = MutableStateFlow(false)
    val showArchivePromptAfterSave: StateFlow<Boolean> = _showArchivePromptAfterSave.asStateFlow()

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
                val beanFlow = beanRepository.observeBean(beanId)
                val brewsFlow = brewRepository.getBrewsForBean(beanId)

                combine(beanFlow, brewsFlow) { bean, brews ->
                    if (bean == null && !_beanDetailState.value.isLoading) {
                        BeanDetailState(isLoading = false, error = "Bean not found or deleted.")
                    } else {
                        BeanDetailState(
                            bean = bean,
                            brews = brews,
                            isLoading = false
                        )
                    }
                }.catch { e ->
                    Log.e("BeanDetailVM", "Error loading details", e)
                    _beanDetailState.update { it.copy(isLoading = false, error = e.message) }
                }.collectLatest { state ->
                    _beanDetailState.value = state
                    if (!isEditing && state.bean != null) {
                        resetEditFieldsToCurrentState()
                    }
                }

            } catch (e: Exception) {
                Log.e("BeanDetailVM", "Error setting up flow", e)
                _beanDetailState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun startEditing() {
        resetEditFieldsToCurrentState()
        isEditing = true
    }

    fun cancelEditing() {
        isEditing = false
        resetEditFieldsToCurrentState()
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

        val isArchivedOnSave = if (remainingWeight == 0.0) currentBean.isArchived else false
        val shouldPromptArchive = remainingWeight == 0.0 && !currentBean.isArchived

        val updatedBean = currentBean.copy(
            name = editName,
            roaster = editRoaster.takeIf { it.isNotBlank() },
            roastDate = roastDate,
            initialWeightGrams = initialWeight,
            remainingWeightGrams = remainingWeight,
            notes = editNotes.takeIf { it.isNotBlank() },
            isArchived = isArchivedOnSave
        )

        viewModelScope.launch {
            try {
                beanRepository.updateBean(updatedBean)
                isEditing = false
                if (shouldPromptArchive) {
                    _showArchivePromptAfterSave.value = true
                }
            } catch (e: Exception) {
                _beanDetailState.update { it.copy(error = "Failed to save: ${e.message}") }
            }
        }
    }

    fun confirmAndArchiveBean() {
        archiveBean { }
        _showArchivePromptAfterSave.value = false
    }

    fun dismissArchivePrompt() {
        _showArchivePromptAfterSave.value = false
    }

    fun archiveBean(onSuccess: () -> Unit = {}) {
        val beanToArchive = _beanDetailState.value.bean ?: return
        viewModelScope.launch {
            try {
                beanRepository.updateBeanArchivedStatus(beanToArchive.id, true)
                _beanDetailState.update { it.copy(bean = it.bean?.copy(isArchived = true)) }
                onSuccess()
            } catch (e: Exception) {
                Log.e("BeanDetailVM", "Kunde inte arkivera böna", e)
                _beanDetailState.update { it.copy(error = "Kunde inte arkivera: ${e.message}") }
            }
        }
    }

    fun unarchiveBean() {
        val beanToUnarchive = _beanDetailState.value.bean
        if (beanToUnarchive != null) {
            viewModelScope.launch {
                try {
                    beanRepository.updateBeanArchivedStatus(beanToUnarchive.id, false)
                    _beanDetailState.update { it.copy(bean = it.bean?.copy(isArchived = false), error = null) }
                } catch (e: Exception) {
                    Log.e("BeanDetailVM", "Kunde inte av-arkivera böna", e)
                    _beanDetailState.update { it.copy(error = "Kunde inte av-arkivera: ${e.message}") }
                }
            }
        }
    }

    fun deleteBean(onSuccess: () -> Unit) {
        val beanToDelete = _beanDetailState.value.bean
        if (beanToDelete != null) {
            viewModelScope.launch {
                try {
                    if (beanToDelete.isArchived) {
                        beanRepository.deleteBean(beanToDelete)
                        onSuccess()
                    } else {
                        _beanDetailState.update { it.copy(error = "Kan endast radera arkiverade bönor.") }
                    }
                } catch (e: Exception) {
                    Log.e("BeanDetailVM", "Failed to delete: ${e.message}", e)
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