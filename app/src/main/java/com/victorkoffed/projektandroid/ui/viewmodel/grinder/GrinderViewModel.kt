package com.victorkoffed.projektandroid.ui.viewmodel.grinder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.victorkoffed.projektandroid.data.db.Grinder
import com.victorkoffed.projektandroid.data.repository.CoffeeRepository
import dagger.hilt.android.lifecycle.HiltViewModel // <-- NY IMPORT
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject // <-- NY IMPORT

/**
 * ViewModel för att hantera CRUD-operationer (Skapa, Läsa, Uppdatera, Radera)
 * relaterade till Grinders (kaffekvarnar).
 */
@HiltViewModel // <-- NY ANNOTERING
class GrinderViewModel @Inject constructor( // <-- NYTT: @Inject constructor
    private val repository: CoffeeRepository // Injiceras av Hilt
) : ViewModel() {

    /**
     * StateFlow som exponerar en lista av alla kaffekvarnar från databasen till UI:et.
     */
    val allGrinders: StateFlow<List<Grinder>> = repository.getAllGrinders()
        .stateIn(
            scope = viewModelScope,
            // Håll flödet aktivt i 5 sekunder efter att den sista observatören försvinner.
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList() // Startar med en tom lista för att undvika null-state.
        )

    /**
     * Lägger till en ny kvarn i databasen om namnet inte är tomt.
     */
    fun addGrinder(name: String, notes: String?) {
        viewModelScope.launch {
            if (name.isNotBlank()) {
                repository.addGrinder(
                    Grinder(
                        name = name,
                        notes = notes // Anteckningar är valfria (nullable)
                    )
                )
            }
        }
    }

    /**
     * Uppdaterar en befintlig Grinder-entitet i databasen.
     * Används vid redigering av en kvarn.
     */
    fun updateGrinder(grinder: Grinder) {
        viewModelScope.launch {
            repository.updateGrinder(grinder)
        }
    }


    /**
     * Raderar en specifik kvarn från databasen.
     */
    fun deleteGrinder(grinder: Grinder) {
        viewModelScope.launch {
            repository.deleteGrinder(grinder)
        }
    }
}