package com.victorkoffed.projektandroid.ui.viewmodel.bean

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.victorkoffed.projektandroid.data.db.Bean
import com.victorkoffed.projektandroid.data.repository.interfaces.BeanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class BeanViewModel @Inject constructor(
    private val beanRepository: BeanRepository
) : ViewModel() {

    val allBeans: StateFlow<List<Bean>> = beanRepository.getAllBeans()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val archivedBeans: StateFlow<List<Bean>> = beanRepository.getArchivedBeans()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private fun parseDateString(dateString: String?): Date? {
        if (dateString.isNullOrBlank()) return null
        return try {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateString)
        } catch (_: ParseException) {
            null
        }
    }

    fun addBean(
        name: String,
        roaster: String?,
        roastDateString: String?,
        initialWeightString: String?,
        remainingWeight: Double,
        notes: String?
    ) {
        val roastDate = parseDateString(roastDateString)
        val initialWeight = initialWeightString?.toDoubleOrNull()

        val newBean = Bean(
            name = name,
            roaster = roaster?.takeIf { it.isNotBlank() },
            roastDate = roastDate,
            initialWeightGrams = initialWeight,
            remainingWeightGrams = remainingWeight,
            notes = notes?.takeIf { it.isNotBlank() },
            isArchived = false
        )
        viewModelScope.launch {
            beanRepository.addBean(newBean)
        }
    }
}