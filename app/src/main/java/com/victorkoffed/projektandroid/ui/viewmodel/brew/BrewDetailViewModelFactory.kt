package com.victorkoffed.projektandroid.ui.viewmodel.brew

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.victorkoffed.projektandroid.data.repository.CoffeeRepository

/**
 * Factory needed to create BrewDetailViewModel instances, providing the
 * CoffeeRepository and the specific brewId.
 */
class BrewDetailViewModelFactory(
    private val repository: CoffeeRepository,
    private val brewId: Long // The ID of the brew to display
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BrewDetailViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            // Pass both dependencies to the ViewModel constructor
            return BrewDetailViewModel(repository, brewId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class for BrewDetailViewModelFactory")
    }
}
