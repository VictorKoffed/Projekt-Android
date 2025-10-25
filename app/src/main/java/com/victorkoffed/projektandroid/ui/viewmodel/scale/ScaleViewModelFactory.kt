package com.victorkoffed.projektandroid.ui.viewmodel.scale

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.victorkoffed.projektandroid.data.repository.CoffeeRepository
import com.victorkoffed.projektandroid.data.repository.ScaleRepository

/**
 * Fabrik för att skapa ScaleViewModel-instanser.
 * Hanterar beroendeinjektion av Application och de två repository-klasserna.
 */
class ScaleViewModelFactory(
    private val application: Application,
    private val scaleRepository: ScaleRepository,
    private val coffeeRepository: CoffeeRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ScaleViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            // Initialiserar ScaleViewModel med alla nödvändiga beroenden.
            return ScaleViewModel(application, scaleRepository, coffeeRepository) as T
        }
        // Kasta undantag om en okänd ViewModel-klass försöker skapas via denna fabrik.
        throw IllegalArgumentException("Unknown ViewModel class for ScaleViewModelFactory")
    }
}
