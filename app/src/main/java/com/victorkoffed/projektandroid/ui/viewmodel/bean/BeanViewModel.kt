package com.victorkoffed.projektandroid.ui.viewmodel.bean

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.victorkoffed.projektandroid.data.db.Bean
import com.victorkoffed.projektandroid.data.repository.CoffeeRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Date

/**
 * ViewModel for managing Coffee Beans.
 */
class BeanViewModel(private val repository: CoffeeRepository) : ViewModel() {

    // Expose a flow of all beans from the repository
    val allBeans: StateFlow<List<Bean>> = repository.getAllBeans()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000), // Keep active for 5s after last subscriber gone
            initialValue = emptyList() // Start with an empty list
        )

    /**
     * Adds a new bean to the database.
     */
    fun addBean(
        name: String,
        roaster: String?,
        roastDate: Date?,
        initialWeight: Double?,
        remainingWeight: Double, // Required
        notes: String?
    ) {
        val newBean = Bean(
            name = name,
            roaster = roaster,
            roastDate = roastDate,
            initialWeightGrams = initialWeight,
            remainingWeightGrams = remainingWeight, // Ensure this is set
            notes = notes
        )
        viewModelScope.launch {
            repository.addBean(newBean)
        }
    }

    /**
     * Updates an existing bean (e.g., when a brew uses some beans).
     * NOTE: Weight deduction is handled by DB Triggers, but this is useful for editing details.
     */
    fun updateBean(bean: Bean) {
        viewModelScope.launch {
            repository.updateBean(bean)
        }
    }

    /**
     * Deletes a bean from the database.
     * OBS: Om bönan har bryggningar kopplade till sig kommer onDelete = ForeignKey.RESTRICT
     * i Brew-tabellen att förhindra raderingen för att skydda historiken.
     * Du kan behöva ändra detta till SET_NULL eller CASCADE om du vill tillåta radering.
     */
    fun deleteBean(bean: Bean) {
        viewModelScope.launch {
            repository.deleteBean(bean) // Antag att deleteBean finns i Repository & DAO
        }
    }
}

