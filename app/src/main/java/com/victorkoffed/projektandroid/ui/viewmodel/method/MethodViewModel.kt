package com.victorkoffed.projektandroid.ui.viewmodel.method

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.victorkoffed.projektandroid.data.db.Method
import com.victorkoffed.projektandroid.data.repository.CoffeeRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for managing brewing Methods.
 */
class MethodViewModel(private val repository: CoffeeRepository) : ViewModel() {

    // Expose a flow of all methods
    val allMethods: StateFlow<List<Method>> = repository.getAllMethods()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /** Adds a new method. */
    fun addMethod(name: String) {
        if (name.isNotBlank()) {
            viewModelScope.launch {
                repository.addMethod(Method(name = name))
            }
        }
    }

    /** Updates an existing method. */
    fun updateMethod(method: Method) {
        viewModelScope.launch {
            repository.updateMethod(method)
        }
    }

    /** Deletes a method. */
    fun deleteMethod(method: Method) {
        viewModelScope.launch {
            repository.deleteMethod(method)
        }
    }
}
