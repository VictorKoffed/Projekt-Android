// app/src/main/java/com/victorkoffed/projektandroid/ui/viewmodel/bean/BeanDetailViewModel.kt
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
import com.victorkoffed.projektandroid.data.repository.CoffeeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
// Ta bort onödig import: import kotlinx.coroutines.flow.map
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

// Annotera med @HiltViewModel
@HiltViewModel
class BeanDetailViewModel @Inject constructor(
    private val repository: CoffeeRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    // Hämta beanId från SavedStateHandle
    private val beanId: Long = savedStateHandle.get<Long>("beanId") ?: throw IllegalArgumentException("beanId not found in SavedStateHandle")

    private val _beanDetailState = MutableStateFlow(BeanDetailState())
    val beanDetailState: StateFlow<BeanDetailState> = _beanDetailState.asStateFlow()

    // NYTT: State för att signalera att arkiveringsdialog ska visas efter sparande
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
                // Använd repository.observeBean för att få reaktiva uppdateringar
                // och hämta bönan oavsett arkivstatus.
                val beanFlow = repository.observeBean(beanId)
                // Hämta bryggningar för bönan
                val brewsFlow = repository.getBrewsForBean(beanId)

                combine(beanFlow, brewsFlow) { bean, brews ->
                    // Om bönan inte hittas (kan hända om den raderas medan vyn är öppen)
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
                    // Uppdatera redigeringsfälten endast om vi *inte* är i redigeringsläge
                    // och om bönan faktiskt finns.
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
        resetEditFieldsToCurrentState() // Säkerställ att fälten är synkade innan redigering
        isEditing = true
    }

    fun cancelEditing() {
        isEditing = false
        // Återställ eventuella osparade ändringar i redigeringsfälten
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

        // Behåll arkivstatus om vikten redan var noll, annars sätt till false om vikten > 0
        val isArchivedOnSave = if (remainingWeight == 0.0) currentBean.isArchived else false
        // Kolla om vikten *blev* noll i denna sparande operation och bönan inte redan är arkiverad
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
                repository.updateBean(updatedBean)
                isEditing = false // Avsluta redigeringsläget
                if (shouldPromptArchive) {
                    _showArchivePromptAfterSave.value = true // Signalera till UI att visa dialogen
                }
            } catch (e: Exception) {
                _beanDetailState.update { it.copy(error = "Failed to save: ${e.message}") }
            }
        }
    }

    /**
     * NYTT: Anropas från UI när användaren bekräftar arkivering från prompten.
     */
    fun confirmAndArchiveBean() {
        archiveBean { /* Inget behov av onSuccess här då vi redan är på detaljsidan */ }
        _showArchivePromptAfterSave.value = false // Återställ flaggan
    }

    /**
     * NYTT: Anropas från UI när användaren avbryter arkivering från prompten.
     */
    fun dismissArchivePrompt() {
        _showArchivePromptAfterSave.value = false // Återställ flaggan
    }


    /**
     * Arkiverar bönan (sätter isArchived till true).
     * @param onSuccess Callback som körs vid lyckad arkivering.
     */
    fun archiveBean(onSuccess: () -> Unit = {}) { // Gör onSuccess valfri
        val beanToArchive = _beanDetailState.value.bean
        if (beanToArchive == null) return

        // Viktkontrollen görs nu explicit i UI innan denna anropas (både manuellt och automatiskt)

        viewModelScope.launch {
            try {
                repository.updateBeanArchivedStatus(beanToArchive.id, true)
                // Uppdatera lokalt state direkt för snabbare UI-respons
                _beanDetailState.update { it.copy(bean = it.bean?.copy(isArchived = true)) }
                onSuccess()
            } catch (e: Exception) {
                Log.e("BeanDetailVM", "Kunde inte arkivera böna", e)
                _beanDetailState.update { it.copy(error = "Kunde inte arkivera: ${e.message}") }
            }
        }
    }

    /**
     * Av-arkiverar bönan genom att sätta isArchived till false.
     */
    fun unarchiveBean() {
        val beanToUnarchive = _beanDetailState.value.bean
        if (beanToUnarchive != null) {
            viewModelScope.launch {
                try {
                    repository.updateBeanArchivedStatus(beanToUnarchive.id, false)
                    // Uppdatera lokalt state direkt
                    _beanDetailState.update { it.copy(bean = it.bean?.copy(isArchived = false), error = null) }
                } catch (e: Exception) {
                    Log.e("BeanDetailVM", "Kunde inte av-arkivera böna", e)
                    _beanDetailState.update { it.copy(error = "Kunde inte av-arkivera: ${e.message}") }
                }
            }
        }
    }

    /**
     * Raderar en böna permanent. Endast tillåtet om bönan är markerad som arkiverad.
     */
    fun deleteBean(onSuccess: () -> Unit) {
        val beanToDelete = _beanDetailState.value.bean
        if (beanToDelete != null) {
            viewModelScope.launch {
                try {
                    if (beanToDelete.isArchived) {
                        repository.deleteBean(beanToDelete)
                        onSuccess()
                    } else {
                        // Felet hanteras nu primärt av UI-logiken (dialogen)
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