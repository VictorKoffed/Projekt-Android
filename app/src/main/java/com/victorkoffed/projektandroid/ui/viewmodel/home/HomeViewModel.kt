package com.victorkoffed.projektandroid.ui.viewmodel.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.victorkoffed.projektandroid.data.themePref.ThemePreferenceManager
import com.victorkoffed.projektandroid.data.db.Brew
import com.victorkoffed.projektandroid.data.repository.CoffeeRepository
import dagger.hilt.android.lifecycle.HiltViewModel // <-- NY IMPORT
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject // <-- NY IMPORT

// Data class för att förenkla visning av en bryggning i listan, inkluderar bönans namn.
data class RecentBrewItem(
    val brew: Brew,
    val beanName: String?
)

/**
 * ViewModel för Hemskärmen. Tillhandahåller statistik och de senaste bryggningarna.
 */
@HiltViewModel // <-- NY ANNOTERING
class HomeViewModel @Inject constructor( // <-- NYTT: @Inject constructor
    repository: CoffeeRepository, // Injiceras av Hilt
    private val themePreferenceManager: ThemePreferenceManager // Injiceras av Hilt
) : ViewModel() {

    // Observera det aktuella mörkerläge-valet i ViewModel
    val isDarkMode: StateFlow<Boolean> = themePreferenceManager.isDarkMode

    /**
     * Växlar mellan ljust och mörkt läge och sparar det manuella valet.
     * Denna funktion kan anropas från en Composable (t.ex. en inställningsknapp).
     */
    fun toggleDarkMode(isDark: Boolean) {
        viewModelScope.launch {
            themePreferenceManager.setManualDarkMode(isDark)
        }
    }

    // StateFlow för de 5 senaste bryggningarna, mappade med respektive bönas namn.
    val recentBrews: StateFlow<List<RecentBrewItem>> = repository.getAllBrews()
        .map { brews -> brews.take(5) } // Begränsa dataströmmen till de 5 senaste bryggningarna.
        .combine(repository.getAllBeans()) { brews, beans ->
            // Kombinera bryggningarna med bönlistan för att slå upp bönans namn (beanName).
            brews.map { brew ->
                val bean = beans.find { it.id == brew.beanId }
                RecentBrewItem(brew = brew, beanName = bean?.name)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // StateFlow för det totala antalet genomförda bryggningar.
    val totalBrewCount: StateFlow<Int> = repository.getAllBrews()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // StateFlow för antalet unika bönor som registrerats, baserat på namn.
    val uniqueBeanCount: StateFlow<Int> = repository.getAllBeans()
        .map { beans -> beans.distinctBy { it.name }.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // StateFlow för total kvarvarande vikt (i gram) av alla registrerade bönor.
    val totalAvailableBeanWeight: StateFlow<Double> = repository.getAllBeans()
        .map { beans -> beans.sumOf { it.remainingWeightGrams } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // StateFlow för tidpunkten då den *senaste* bryggningen startade.
    val lastBrewTime: StateFlow<Date?> = repository.getAllBrews()
        .map { brews -> brews.firstOrNull()?.startedAt } // Repository returnerar de senaste först.
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )
}