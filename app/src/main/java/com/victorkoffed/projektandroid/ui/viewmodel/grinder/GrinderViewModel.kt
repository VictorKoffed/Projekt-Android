package com.victorkoffed.projektandroid.ui.viewmodel.grinder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.victorkoffed.projektandroid.data.db.Grinder
import com.victorkoffed.projektandroid.data.repository.CoffeeRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel för att hantera logik relaterad till Grinders (kvarnar).
 */
class GrinderViewModel(private val repository: CoffeeRepository) : ViewModel() {

    // Exponerar en Flow av alla kvarnar från databasen.
    val allGrinders: StateFlow<List<Grinder>> = repository.getAllGrinders()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000), // Håll flödet aktivt i 5s
            initialValue = emptyList() // Börja med en tom lista
        )

    /**
     * Lägger till en ny kvarn i databasen.
     */
    fun addGrinder(name: String, notes: String?) { // Ändrade notes till nullable för att matcha Grinder data class
        // Kör på en bakgrundstråd
        viewModelScope.launch {
            if (name.isNotBlank()) {
                repository.addGrinder(
                    Grinder(
                        name = name,
                        notes = notes // Använd notes direkt (kan vara null)
                    )
                )
            }
        }
    }

    /**
     * Uppdaterar en befintlig kvarn i databasen.
     * Denna funktion lades till för att stödja redigering.
     */
    fun updateGrinder(grinder: Grinder) { // DENNA FUNKTION LADES TILL
        viewModelScope.launch {
            repository.updateGrinder(grinder)
        }
    }


    /**
     * Raderar en kvarn från databasen.
     */
    fun deleteGrinder(grinder: Grinder) {
        viewModelScope.launch {
            repository.deleteGrinder(grinder)
        }
    }
}

