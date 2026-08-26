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
 * ViewModel managing coffee brewing method inventory lists and handling CRUD operations
 * for brewing method profiles.
 */
@HiltViewModel
class MethodViewModel @Inject constructor(
    private val methodRepository: MethodRepository
) : ViewModel() {

    val allMethods: StateFlow<List<Method>> = methodRepository.getAllMethods()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addMethod(name: String) {
        if (name.isNotBlank()) {
            viewModelScope.launch {
                methodRepository.addMethod(Method(name = name))
            }
        }
    }

    fun updateMethod(method: Method) {
        viewModelScope.launch {
            methodRepository.updateMethod(method)
        }
    }

    fun deleteMethod(method: Method) {
        viewModelScope.launch {
            methodRepository.deleteMethod(method)
        }
    }
}