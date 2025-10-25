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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Data class för att hålla all state för bönans detaljskärm.
 * Inkluderar bönan, tillhörande bryggningar och UI-status.
 */
data class BeanDetailState(
    val bean: Bean? = null,
    val brews: List<Brew> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

class BeanDetailViewModel(
    private val repository: CoffeeRepository,
    private val beanId: Long // ID för den böna som ska visas
) : ViewModel() {

    private val _beanDetailState = MutableStateFlow(BeanDetailState())
    // Exponera StateFlow för Compose UI
    val beanDetailState: StateFlow<BeanDetailState> = _beanDetailState.asStateFlow()

    // State som kontrollerar om UI är i redigeringsläge
    var isEditing by mutableStateOf(false)
        private set

    // States för att hålla redigerade värden i redigeringsdialogen/vyn
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

    /**
     * Laddar bönans detaljer och dess tillhörande bryggningar.
     * Använder Flow.combine för att synkronisera båda dataströmmarna.
     */
    private fun loadBeanDetails() {
        viewModelScope.launch {
            _beanDetailState.update { it.copy(isLoading = true, error = null) }
            try {
                // Skapa ett flöde för att isolera den specifika bönan från hela listan
                val beanFlow = repository.getAllBeans().map { list -> list.find { it.id == beanId } }
                // Flöde för alla bryggningar som är kopplade till denna böna
                val brewsFlow = repository.getBrewsForBean(beanId)

                // Kombinera båda flödena till ett enda StateFlow för UI
                combine(beanFlow, brewsFlow) { bean, brews ->
                    // Returnerar det färdiga State-objektet
                    BeanDetailState(
                        bean = bean,
                        brews = brews,
                        isLoading = false
                    )
                }.catch { e ->
                    // Hantera eventuella databasfel i flödespipelinen
                    Log.e("BeanDetailVM", "Error loading details", e)
                    _beanDetailState.update { it.copy(isLoading = false, error = e.message) }
                }.collectLatest { state ->
                    // Uppdatera det exponerade state-värdet
                    _beanDetailState.value = state
                    // Uppdatera redigeringsfälten med den nya datan om vi inte redan redigerar
                    if (!isEditing) {
                        resetEditFieldsToCurrentState()
                    }
                }

            } catch (e: Exception) {
                // Hantera fel under initiering/flödesuppsättning
                Log.e("BeanDetailVM", "Error setting up flow", e)
                _beanDetailState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    // --- Redigeringslogik ---

    /**
     * Sätter ViewModel i redigeringsläge och återställer redigeringsfälten till nuvarande värden.
     */
    fun startEditing() {
        resetEditFieldsToCurrentState()
        isEditing = true
    }

    /**
     * Avbryter redigeringsläget.
     */
    fun cancelEditing() {
        isEditing = false
    }

    /**
     * Försöker parsa ett datum i formatet "yyyy-MM-dd".
     */
    private fun parseDateString(dateString: String?): Date? {
        if (dateString.isNullOrBlank()) return null
        return try {
            dateFormat.parse(dateString)
        } catch (_: ParseException) {
            // Ignorera parsningsfel, returnera null istället
            null
        }
    }

    /**
     * Sparar ändringar till databasen.
     */
    fun saveChanges() {
        val currentBean = _beanDetailState.value.bean ?: return
        val remainingWeight = editRemainingWeightStr.toDoubleOrNull()

        // Validering: Namn och återstående vikt måste finnas och vara giltiga
        if (editName.isBlank() || remainingWeight == null || remainingWeight < 0) {
            // Användarfeedback om validering bör ske i UI, men detta förhindrar ogiltiga DB-anrop
            _beanDetailState.update { it.copy(error = "Name and remaining weight are required and must be valid.") }
            return
        }

        val roastDate = parseDateString(editRoastDateStr)
        val initialWeight = editInitialWeightStr.toDoubleOrNull()

        // Skapa en kopia av Bean-objektet med de nya fälten
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
                // Flowet (loadBeanDetails) triggar automatiskt en UI-uppdatering efter sparning
            } catch (e: Exception) {
                _beanDetailState.update { it.copy(error = "Failed to save: ${e.message}") }
            }
        }
    }

    /**
     * Raderar den aktuella bönan från databasen.
     *
     * @param onSuccess Callback som anropas när raderingen lyckats (används för navigering).
     */
    fun deleteBean(onSuccess: () -> Unit) {
        val beanToDelete = _beanDetailState.value.bean
        if (beanToDelete != null) {
            viewModelScope.launch {
                try {
                    repository.deleteBean(beanToDelete)
                    onSuccess() // Navigera tillbaka (handleds i UI)
                } catch (e: Exception) {
                    _beanDetailState.update { it.copy(error = "Failed to delete: ${e.message}") }
                }
            }
        }
    }

    /**
     * Återställer alla redigeringsfält till de aktuella värdena från BeanDetailState.
     */
    private fun resetEditFieldsToCurrentState() {
        val bean = _beanDetailState.value.bean
        editName = bean?.name ?: ""
        editRoaster = bean?.roaster ?: ""
        // Formatera Date till String för visning i textfält
        editRoastDateStr = bean?.roastDate?.let { dateFormat.format(it) } ?: ""
        // Använd toString() för Double-värden
        editInitialWeightStr = bean?.initialWeightGrams?.toString() ?: ""
        editRemainingWeightStr = bean?.remainingWeightGrams?.toString() ?: ""
        editNotes = bean?.notes ?: ""
    }

    /**
     * Nollställer felmeddelandet efter att det har visats i UI.
     */
    fun clearError() {
        _beanDetailState.update { it.copy(error = null) }
    }
}