package com.victorkoffed.projektandroid.ui.viewmodel.method

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.victorkoffed.projektandroid.data.db.Method
import com.victorkoffed.projektandroid.data.repository.interfaces.MethodRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel för hantering av bryggmetoder (Methods).
 *
 * Använder [MethodRepository] för all datalagerinteraktion.
 */
@HiltViewModel
class MethodViewModel @Inject constructor(
    // RÄTTNING: Byt från 'repository: CoffeeRepository' till 'methodRepository: MethodRepository'
    private val methodRepository: MethodRepository
) : ViewModel() {

    /**
     * Exponerar alla lagrade [Method]-objekt som en [StateFlow].
     *
     * Använder `stateIn` för att konvertera flödet från databasen till en StateFlow,
     * vilket är optimalt för UI-bindning i Jetpack Compose/Views.
     */
    val allMethods: StateFlow<List<Method>> = methodRepository.getAllMethods() // <-- RÄTTAD
        .stateIn(
            scope = viewModelScope,
            // Håller flödet aktivt i 5 sekunder efter att den sista observatören försvinner.
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * Lägger till en ny bryggmetod i databasen om namnet inte är tomt.
     */
    fun addMethod(name: String) {
        if (name.isNotBlank()) {
            viewModelScope.launch {
                methodRepository.addMethod(Method(name = name)) // <-- RÄTTAD
            }
        }
    }

    /**
     * Uppdaterar en befintlig bryggmetod i databasen.
     */
    fun updateMethod(method: Method) {
        viewModelScope.launch {
            methodRepository.updateMethod(method) // <-- RÄTTAD
        }
    }

    /**
     * Tar bort en bryggmetod från databasen.
     */
    fun deleteMethod(method: Method) {
        viewModelScope.launch {
            methodRepository.deleteMethod(method) // <-- RÄTTAD
        }
    }
}