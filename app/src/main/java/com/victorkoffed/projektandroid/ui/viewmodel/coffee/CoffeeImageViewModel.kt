package com.victorkoffed.projektandroid.ui.viewmodel.coffee

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.victorkoffed.projektandroid.data.repository.interfaces.CoffeeImageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CoffeeImageViewModel @Inject constructor(
    private val imageRepository: CoffeeImageRepository
) : ViewModel() {

    val imageUrl = mutableStateOf<String?>(null)
    val loading = mutableStateOf(false)
    val error = mutableStateOf<String?>(null)

    fun loadRandomCoffeeImage() {
        if (loading.value) return

        loading.value = true
        error.value = null

        viewModelScope.launch {
            try {
                val url = imageRepository.fetchRandomCoffeeImageUrl()
                imageUrl.value = url
                if (url == null) {
                    error.value = "Could not find image URL in API response."
                }
            } catch (e: Exception) {
                error.value = "Network error: ${e.message ?: "Unknown error"}"
            } finally {
                loading.value = false
            }
        }
    }
}