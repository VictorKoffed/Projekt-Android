package com.victorkoffed.projektandroid.ui.viewmodel.method

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.victorkoffed.projektandroid.data.repository.CoffeeRepository

/**
 * Fabrik för att skapa MethodViewModel-instanser med beroendeinjektion av CoffeeRepository.
 *
 * Används för att injecta repositoryt i ViewModellen, vilket är nödvändigt när
 * ViewModellen har en konstruktor som tar argument (t.ex. ett repository).
 */
class MethodViewModelFactory(private val repository: CoffeeRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MethodViewModel::class.java)) {
            // Skapar MethodViewModel och skickar med repositoryt.
            @Suppress("UNCHECKED_CAST")
            return MethodViewModel(repository) as T
        }
        throw IllegalArgumentException("Okänd ViewModel-klass")
    }
}