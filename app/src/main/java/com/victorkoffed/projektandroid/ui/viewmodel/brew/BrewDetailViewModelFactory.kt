package com.victorkoffed.projektandroid.ui.viewmodel.brew

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.victorkoffed.projektandroid.data.repository.CoffeeRepository

/**
 * Factory som krävs för att skapa instanser av BrewDetailViewModel,
 * och förser den med CoffeeRepository och det specifika brygg-ID:t (brewId).
 *
 * Denna fabrik är nödvändig eftersom BrewDetailViewModel behöver argument
 * (repository och brewId) vid konstruktion.
 */
class BrewDetailViewModelFactory(
    private val repository: CoffeeRepository,
    private val brewId: Long // ID:t för den bryggning som ska visas/redigeras.
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BrewDetailViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return BrewDetailViewModel(repository, brewId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class for BrewDetailViewModelFactory")
    }
}