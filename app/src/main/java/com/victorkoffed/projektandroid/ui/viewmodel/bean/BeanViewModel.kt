package com.victorkoffed.projektandroid.ui.viewmodel.bean

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.victorkoffed.projektandroid.data.db.Bean
import com.victorkoffed.projektandroid.data.repository.CoffeeRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.ParseException // För datum-parsning
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    // Hjälpfunktion för att försöka parsa datumsträng (yyyy-MM-dd)
    private fun parseDateString(dateString: String?): Date? {
        if (dateString.isNullOrBlank()) return null
        return try {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateString)
        } catch (e: ParseException) {
            null // Returnera null om formatet är fel
        }
    }

    /**
     * Adds a new bean to the database.
     * Tar nu emot roastDateString och initialWeightString.
     */
    fun addBean(
        name: String,
        roaster: String?,
        roastDateString: String?, // NY: Datum som sträng
        initialWeightString: String?, // NY: Vikt som sträng
        remainingWeight: Double,
        notes: String?
    ) {
        val roastDate = parseDateString(roastDateString) // Konvertera sträng till Date
        val initialWeight = initialWeightString?.toDoubleOrNull() // Konvertera sträng till Double

        val newBean = Bean(
            name = name,
            roaster = roaster,
            roastDate = roastDate, // Spara Date-objektet
            initialWeightGrams = initialWeight, // Spara Double-värdet
            remainingWeightGrams = remainingWeight,
            notes = notes
        )
        viewModelScope.launch {
            repository.addBean(newBean)
        }
    }

    /**
     * Updates an existing bean.
     * Tar nu emot roastDateString och initialWeightString.
     */
    fun updateBean(
        bean: Bean, // Tar emot det gamla objektet
        // Tar emot de nya värdena som strängar
        name: String,
        roaster: String?,
        roastDateString: String?,
        initialWeightString: String?,
        remainingWeight: Double,
        notes: String?
    ) {
        val roastDate = parseDateString(roastDateString)
        val initialWeight = initialWeightString?.toDoubleOrNull()

        // Skapa en kopia av det gamla objektet med uppdaterade värden
        val updatedBean = bean.copy(
            name = name,
            roaster = roaster,
            roastDate = roastDate,
            initialWeightGrams = initialWeight,
            remainingWeightGrams = remainingWeight,
            notes = notes
        )
        viewModelScope.launch {
            repository.updateBean(updatedBean)
        }
    }

    /** Deletes a bean from the database. */
    fun deleteBean(bean: Bean) {
        viewModelScope.launch {
            repository.deleteBean(bean)
        }
    }
}