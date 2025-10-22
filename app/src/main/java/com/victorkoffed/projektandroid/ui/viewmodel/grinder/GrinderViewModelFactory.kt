package com.victorkoffed.projektandroid.ui.viewmodel.grinder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.victorkoffed.projektandroid.data.repository.CoffeeRepository

/**
 * En Factory-klass som behövs för att skicka vårt CoffeeRepository
 * till GrinderViewModels konstruktor.
 */
class GrinderViewModelFactory(
    private val repository: CoffeeRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GrinderViewModel::class.java)) {
            return GrinderViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
