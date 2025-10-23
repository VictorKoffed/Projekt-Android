package com.victorkoffed.projektandroid.ui.viewmodel.bean

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.victorkoffed.projektandroid.data.db.Bean
import com.victorkoffed.projektandroid.data.repository.CoffeeRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
// Ta bort onödiga importer för sträng-parsning
// import java.text.ParseException
// import java.text.SimpleDateFormat
import java.util.Date
// import java.util.Locale

/**
 * ViewModel for managing Coffee Beans.
 */
class BeanViewModel(private val repository: CoffeeRepository) : ViewModel() {

    // Expose a flow of all beans from the repository
    val allBeans: StateFlow<List<Bean>> = repository.getAllBeans()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Ta bort parseDateString - behövs inte längre
    // private fun parseDateString(dateString: String?): Date? { ... }

    /**
     * Adds a new bean to the database.
     * Tar nu emot Date? och Double? direkt.
     */
    fun addBean(
        name: String,
        roaster: String?,
        // --- ÄNDRADE PARAMETRAR ---
        roastDate: Date?,        // Tar emot Date?
        initialWeight: Double?,  // Tar emot Double?
        // --- SLUT PÅ ÄNDRING ---
        remainingWeight: Double,
        notes: String?
    ) {
        // Ingen konvertering behövs här längre

        val newBean = Bean(
            name = name,
            roaster = roaster,
            roastDate = roastDate, // Använd direkt
            initialWeightGrams = initialWeight, // Använd direkt
            remainingWeightGrams = remainingWeight,
            notes = notes
        )
        viewModelScope.launch {
            repository.addBean(newBean)
        }
    }

    /**
     * Updates an existing bean using a Bean object.
     * (Förenklad version som tar emot ett uppdaterat Bean-objekt)
     */
    fun updateBean(updatedBean: Bean) { // Tar bara emot det uppdaterade objektet
        viewModelScope.launch {
            // Se till att ID är korrekt (borde vara det från copy())
            // Ingen mer konvertering behövs här
            repository.updateBean(updatedBean)
        }
    }

    // Alternativ updateBean om du föredrar att skicka separata värden (behåll den gamla logiken med nya typer)
    /*
    fun updateBean(
        bean: Bean, // Tar emot det gamla objektet
        name: String,
        roaster: String?,
        roastDate: Date?,        // Tar emot Date?
        initialWeight: Double?,  // Tar emot Double?
        remainingWeight: Double,
        notes: String?
    ) {
        // Ingen konvertering behövs här längre

        // Skapa en kopia av det gamla objektet med uppdaterade värden
        val updatedBean = bean.copy(
            name = name,
            roaster = roaster,
            roastDate = roastDate, // Använd direkt
            initialWeightGrams = initialWeight, // Använd direkt
            remainingWeightGrams = remainingWeight,
            notes = notes
        )
        viewModelScope.launch {
            repository.updateBean(updatedBean)
        }
    }
    */


    /** Deletes a bean from the database. */
    fun deleteBean(bean: Bean) {
        viewModelScope.launch {
            repository.deleteBean(bean)
        }
    }
}