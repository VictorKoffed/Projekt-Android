package com.victorkoffed.projektandroid.ui.viewmodel.brew

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.victorkoffed.projektandroid.data.repository.CoffeeRepository

/**
 * Factory for creating BrewViewModel instances.
 */
class BrewViewModelFactory(private val repository: CoffeeRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BrewViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return BrewViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
