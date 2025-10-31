package com.victorkoffed.projektandroid.ui.viewmodel.coffee

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.victorkoffed.projektandroid.data.repository.CoffeeImageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel för att hantera logiken kring att hämta en slumpmässig kaffebild från ett externt API.
 * Använder en Hilt-injicerad Repository och Coroutines för nätverksanrop.
 */
@HiltViewModel
class CoffeeImageViewModel @Inject constructor(
    private val imageRepository: CoffeeImageRepository
) : ViewModel() {

    // State för den slumpmässigt hämtade bildens URL.
    val imageUrl = mutableStateOf<String?>(null)
    // Indikerar om en nätverksförfrågan pågår.
    val loading = mutableStateOf(false)
    // Håller ett eventuellt felmeddelande från nätverksanropet.
    val error = mutableStateOf<String?>(null)

    /**
     * Utför ett nätverksanrop för att hämta URL:en till en slumpmässig kaffebild från API:t
     * och uppdaterar det observerbara Compose-state:t.
     */
    fun loadRandomCoffeeImage() {
        if (loading.value) return

        loading.value = true
        error.value = null // Rensas vid varje nytt försök

        viewModelScope.launch {
            try {
                val url = imageRepository.fetchRandomCoffeeImageUrl()
                imageUrl.value = url
                if (url == null) {
                    // Engelsk feltext vid API-svar utan bild
                    error.value = "Could not find image URL in API response."
                }
            } catch (e: Exception) {
                // Engelsk feltext vid nätverksfel (t.ex. ingen internetuppkoppling)
                error.value = "Network error: ${e.message ?: "Unknown error"}"
            } finally {
                loading.value = false
            }
        }
    }

}