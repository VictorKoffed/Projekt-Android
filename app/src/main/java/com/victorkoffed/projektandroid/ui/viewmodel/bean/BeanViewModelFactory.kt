package com.victorkoffed.projektandroid.ui.viewmodel.bean

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.victorkoffed.projektandroid.data.repository.CoffeeRepository

/**
 * Factory-klass som krävs för att korrekt instansiera BeanViewModel.
 *
 * Denna Factory är nödvändig eftersom BeanViewModel har konstruktorargument
 * (CoffeeRepository) som måste injiceras. Standard ViewModel Factory kan endast
 * skapa ViewModels som har en argumentlös konstruktor.
 */
class BeanViewModelFactory(
    private val repository: CoffeeRepository // Beroendet som ska injiceras
) : ViewModelProvider.Factory {

    /**
     * Skapar en ny instans av den specificerade ViewModel-klassen.
     */
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        // Kontrollera att den begärda klassen är BeanViewModel
        if (modelClass.isAssignableFrom(BeanViewModel::class.java)) {
            // Skapa och returnera ViewModel-instansen med det injicerade repositoryt
            @Suppress("UNCHECKED_CAST")
            return BeanViewModel(repository) as T
        }
        // Kasta ett fel om en okänd ViewModel-klass begärs
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}