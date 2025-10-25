package com.victorkoffed.projektandroid.ui.viewmodel.bean

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.victorkoffed.projektandroid.data.repository.CoffeeRepository

/**
 * Factory-klass som krävs för att korrekt instansiera BeanDetailViewModel.
 *
 * Denna klass är nödvändig eftersom BeanDetailViewModel har konstruktorargument
 * (repository och beanId) som måste injiceras manuellt, vilket standard ViewModel
 * Factory (som skapas av Android-ramverket) inte kan hantera.
 */
class BeanDetailViewModelFactory(
    private val repository: CoffeeRepository, // Beroende 1: Dataåtkomst
    private val beanId: Long // Beroende 2: ID:t för den specifika bönan
) : ViewModelProvider.Factory {

    /**
     * Skapar en ny instans av den specificerade ViewModel-klassen.
     */
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        // Kontrollera att den begärda klassen är BeanDetailViewModel
        if (modelClass.isAssignableFrom(BeanDetailViewModel::class.java)) {
            // Skapa och returnera ViewModel-instansen med de injicerade beroendena
            @Suppress("UNCHECKED_CAST")
            return BeanDetailViewModel(repository, beanId) as T
        }
        // Kasta ett fel om en okänd ViewModel-klass begärs
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}