package com.victorkoffed.projektandroid.ui.viewmodel.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.victorkoffed.projektandroid.data.db.Bean
import com.victorkoffed.projektandroid.data.db.Brew
import com.victorkoffed.projektandroid.data.repository.CoffeeRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

// Data class för att hålla data som behövs för en bryggning i listan
data class RecentBrewItem(
    val brew: Brew,
    val beanName: String? // Lägg till bönans namn för enkel visning
)

/**
 * ViewModel for the Home screen.
 */
class HomeViewModel(private val repository: CoffeeRepository) : ViewModel() {

    // Hämta de 5 senaste bryggningarna
    val recentBrews: StateFlow<List<RecentBrewItem>> = repository.getAllBrews()
        .map { brews -> brews.take(5) } // Begränsa till de 5 senaste
        .combine(repository.getAllBeans()) { brews, beans -> // Kombinera med bönlistan
            brews.map { brew ->
                val bean = beans.find { it.id == brew.beanId } // Hitta matchande böna
                RecentBrewItem(brew = brew, beanName = bean?.name) // Skapa objektet för listan
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Placeholder för antal bryggningar (kan hämtas mer effektivt senare)
    val totalBrewCount: StateFlow<Int> = repository.getAllBrews()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Placeholder för antal unika bönor (kan hämtas mer effektivt senare)
    val uniqueBeanCount: StateFlow<Int> = repository.getAllBeans()
        .map { beans -> beans.distinctBy { it.name }.size } // Räkna unika namn
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Placeholder för total vikt av tillgängliga bönor
    val totalAvailableBeanWeight: StateFlow<Double> = repository.getAllBeans()
        .map { beans -> beans.sumOf { it.remainingWeightGrams } } // Summera kvarvarande vikt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // TODO: Hämta data för "Time without coffee" och "Connected status"
}
