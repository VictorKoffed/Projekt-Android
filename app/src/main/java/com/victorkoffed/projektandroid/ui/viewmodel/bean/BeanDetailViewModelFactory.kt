package com.victorkoffed.projektandroid.ui.viewmodel.bean

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.victorkoffed.projektandroid.data.repository.CoffeeRepository

class BeanDetailViewModelFactory(
    private val repository: CoffeeRepository,
    private val beanId: Long
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BeanDetailViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return BeanDetailViewModel(repository, beanId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}