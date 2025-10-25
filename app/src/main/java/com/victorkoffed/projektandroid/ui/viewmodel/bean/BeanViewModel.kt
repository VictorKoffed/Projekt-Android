package com.victorkoffed.projektandroid.ui.viewmodel.bean

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.victorkoffed.projektandroid.data.db.Bean
import com.victorkoffed.projektandroid.data.repository.CoffeeRepository
import dagger.hilt.android.lifecycle.HiltViewModel // <-- NY IMPORT
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject // <-- NY IMPORT

/**
 * ViewModel för hantering av Kaffebönor.
 * Huvudansvaret är att tillhandahålla listan över alla bönor och hantera tillägg av nya.
 * (Redigering/Borttagning hanteras nu i BeanDetailViewModel).
 */
@HiltViewModel // <-- NY ANNOTERING
class BeanViewModel @Inject constructor( // <-- NYTT: @Inject constructor
    private val repository: CoffeeRepository // Injiceras av Hilt
) : ViewModel() {

    /**
     * Exponerar ett StateFlow av alla bönor från databasen.
     * StateIn används för att konvertera Flow till StateFlow, vilket gör den livscykelmedveten.
     */
    val allBeans: StateFlow<List<Bean>> = repository.getAllBeans()
        .stateIn(
            scope = viewModelScope,
            // Dela flödet och håll det aktivt i 5 sekunder efter att sista observatören försvinner
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList() // Startvärde
        )

    /**
     * Hjälpfunktion för att försöka parsa en datumsträng i formatet "yyyy-MM-dd".
     *
     * Returnerar: Date-objekt vid lyckad parsing, annars null.
     */
    private fun parseDateString(dateString: String?): Date? {
        if (dateString.isNullOrBlank()) return null
        return try {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateString)
        } catch (_: ParseException) {
            // Returnera null om formatet är felaktigt
            null
        }
    }

    /**
     * Lägger till en ny böna i databasen.
     * Hanterar konvertering av stränginput från UI (roastDateString, initialWeightString)
     * till de korrekta typerna (Date, Double) för datamodellen.
     */
    fun addBean(
        name: String,
        roaster: String?,
        roastDateString: String?,
        initialWeightString: String?,
        remainingWeight: Double,
        notes: String?
    ) {
        // Försök konvertera datumsträng till Date
        val roastDate = parseDateString(roastDateString)
        // Försök konvertera viktsträng till Double
        val initialWeight = initialWeightString?.toDoubleOrNull()

        val newBean = Bean(
            name = name,
            roaster = roaster?.takeIf { it.isNotBlank() }, // Spara endast om icke-tom
            roastDate = roastDate,
            initialWeightGrams = initialWeight,
            remainingWeightGrams = remainingWeight,
            notes = notes?.takeIf { it.isNotBlank() } // Spara endast om icke-tom
        )
        viewModelScope.launch {
            // Utför databasoperationen asynkront
            repository.addBean(newBean)
        }
    }
}