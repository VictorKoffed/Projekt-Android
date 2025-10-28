package com.victorkoffed.projektandroid.ui.viewmodel.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.victorkoffed.projektandroid.data.db.Brew
import com.victorkoffed.projektandroid.data.repository.CoffeeRepository
import com.victorkoffed.projektandroid.data.themePref.ThemePreferenceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map // Se till att map är importerad
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

// Data class för att förenkla visning av en bryggning i listan, inkluderar bönans namn.
data class RecentBrewItem(
    val brew: Brew,
    val beanName: String?
)

/**
 * ViewModel för HomeScreen.
 * Hanterar logik för att hämta senaste bryggningar, räkna bönor och bryggningar,
 * och hantera inställningar som Dark Mode.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: CoffeeRepository,
    private val themePreferenceManager: ThemePreferenceManager
) : ViewModel() {

    // Hämtar de 5 senaste bryggningarna (som inte är kopplade till arkiverade bönor)
    // för visning på hemskärmen. Kombinerar med bönnamn.
    val recentBrews: StateFlow<List<RecentBrewItem>> = repository.getAllBrews()
        .map { brews -> brews.take(5) }
        .combine(repository.getAllBeans()) { brews, beans ->
            brews.map { brew ->
                val bean = beans.find { it.id == brew.beanId }
                RecentBrewItem(brew = brew, beanName = bean?.name ?: "Unknown Bean")
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Hämtar det totala antalet bönor (aktiva + arkiverade)
    val beansExploredCount: StateFlow<Int> = repository.getTotalBeanCount()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    // UPPDATERAD: StateFlow för det totala antalet genomförda bryggningar (ALLA)
    val totalBrewCount: StateFlow<Int> = repository.getTotalBrewCount() // Använder den nya metoden
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // --- Dark Mode Hantering ---
    val isDarkMode: StateFlow<Boolean> = themePreferenceManager.isDarkMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    fun toggleDarkMode(enable: Boolean) {
        viewModelScope.launch {
            themePreferenceManager.setManualDarkMode(enable)
        }
    }

    // StateFlow för total kvarvarande vikt (i gram) av alla AKTIVA bönor.
    val totalAvailableBeanWeight: StateFlow<Double> = repository.getAllBeans()
        .map { beans -> beans.sumOf { it.remainingWeightGrams } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // StateFlow för tidpunkten då den *senaste* AKTIVA bryggningen startade.
    val lastBrewTime: StateFlow<Date?> = repository.getAllBrews()
        .map { brews -> brews.firstOrNull()?.startedAt }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )
}