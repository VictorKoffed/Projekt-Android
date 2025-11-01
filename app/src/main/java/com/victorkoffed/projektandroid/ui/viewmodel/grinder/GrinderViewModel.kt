package com.victorkoffed.projektandroid.ui.viewmodel.grinder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.victorkoffed.projektandroid.data.db.Grinder
import com.victorkoffed.projektandroid.data.repository.interfaces.GrinderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GrinderViewModel @Inject constructor(
    private val grinderRepository: GrinderRepository // <-- ÄNDRAD
) : ViewModel() {

    val allGrinders: StateFlow<List<Grinder>> = grinderRepository.getAllGrinders() // <-- ÄNDRAD
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addGrinder(name: String, notes: String?) {
        viewModelScope.launch {
            if (name.isNotBlank()) {
                grinderRepository.addGrinder( // <-- ÄNDRAD
                    Grinder(
                        name = name,
                        notes = notes
                    )
                )
            }
        }
    }

    fun updateGrinder(grinder: Grinder) {
        viewModelScope.launch {
            grinderRepository.updateGrinder(grinder) // <-- ÄNDRAD
        }
    }

    fun deleteGrinder(grinder: Grinder) {
        viewModelScope.launch {
            grinderRepository.deleteGrinder(grinder) // <-- ÄNDRAD
        }
    }
}