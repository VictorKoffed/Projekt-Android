package com.victorkoffed.projektandroid.ui.viewmodel.grinder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.victorkoffed.projektandroid.data.repository.CoffeeRepository

/**
 * Factory som används för att skapa instanser av GrinderViewModel.
 *
 * Denna fabrik tillåter oss att injicera CoffeeRepository i ViewModel-konstruktorn,
 * vilket är nödvändigt för att hantera kvarn-data.
 */
class GrinderViewModelFactory(
    private val repository: CoffeeRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GrinderViewModel::class.java)) {
            // Skickar in repository till ViewModel-konstruktorn.
            return GrinderViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}