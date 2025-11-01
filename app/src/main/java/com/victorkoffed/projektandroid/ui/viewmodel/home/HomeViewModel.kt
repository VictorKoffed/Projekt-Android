@file:Suppress("CanBeParameter")

package com.victorkoffed.projektandroid.ui.viewmodel.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.victorkoffed.projektandroid.data.db.Brew
import com.victorkoffed.projektandroid.data.repository.interfaces.BeanRepository
import com.victorkoffed.projektandroid.data.repository.interfaces.BrewRepository
import com.victorkoffed.projektandroid.data.themePref.ThemePreferenceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

data class RecentBrewItem(
    val brew: Brew,
    val beanName: String?
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val brewRepository: BrewRepository,
    private val beanRepository: BeanRepository,
    private val themePreferenceManager: ThemePreferenceManager
) : ViewModel() {

    val recentBrews: StateFlow<List<RecentBrewItem>> = brewRepository.getAllBrews()
        .map { brews -> brews.take(5) }
        .combine(beanRepository.getAllBeans()) { brews, beans ->
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

    val beansExploredCount: StateFlow<Int> = beanRepository.getTotalBeanCount()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    val totalBrewCount: StateFlow<Int> = brewRepository.getTotalBrewCount()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

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

    val totalAvailableBeanWeight: StateFlow<Double> = beanRepository.getAllBeans()
        .map { beans -> beans.sumOf { it.remainingWeightGrams } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val lastBrewTime: StateFlow<Date?> = brewRepository.getAllBrewsIncludingArchived()
        .map { brews -> brews.firstOrNull()?.startedAt }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )
}