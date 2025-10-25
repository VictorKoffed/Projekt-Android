package com.victorkoffed.projektandroid.ui.viewmodel.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.victorkoffed.projektandroid.data.repository.CoffeeRepository

/**
 * Fabrik för att skapa HomeViewModel-instanser med den specificerade repoun.
 * Detta säkerställer att ViewModel får sin CoffeeRepository injicerad.
 */
class HomeViewModelFactory(private val repository: CoffeeRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            // Kastar säkert till T eftersom vi just har kontrollerat att det är HomeViewModel.
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(repository) as T
        }
        throw IllegalArgumentException("Okänd ViewModel-klass")
    }
}