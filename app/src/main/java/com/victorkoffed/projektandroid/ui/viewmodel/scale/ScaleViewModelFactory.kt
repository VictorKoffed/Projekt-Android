package com.victorkoffed.projektandroid.ui.viewmodel.scale

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.victorkoffed.projektandroid.data.repository.CoffeeRepository
import com.victorkoffed.projektandroid.data.repository.ScaleRepository

/**
 * Factory for creating ScaleViewModel instances with multiple repository dependencies.
 */
class ScaleViewModelFactory(
    private val application: Application,
    private val scaleRepository: ScaleRepository,
    private val coffeeRepository: CoffeeRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ScaleViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            // Skickar in alla tre beroenden till ScaleViewModel
            return ScaleViewModel(application, scaleRepository, coffeeRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class for ScaleViewModelFactory")
    }
}

