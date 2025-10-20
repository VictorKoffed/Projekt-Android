package com.victorkoffed.projektandroid.data.viewmodel

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.victorkoffed.projektandroid.data.network.NetworkRequestQueue

/**
 * ViewModel för att hantera logiken kring att hämta en slumpmässig kaffebild.
 * Använder Volley för nätverksanrop och håller state som UI observerar via Compose.
 */
class CoffeeImageViewModel(app: Application) : AndroidViewModel(app) {

    // Håller aktuell bild-URL, laddningsstatus och ev. felmeddelande
    val imageUrl = mutableStateOf<String?>(null)
    val loading = mutableStateOf(false)
    val error = mutableStateOf<String?>(null)

    // Skapar en referens till vår gemensamma nätverkskö (Singleton)
    private val queue = NetworkRequestQueue.getInstance(app).queue

    companion object {
        private const val RANDOM_COFFEE_API_URL = "https://coffee.alexflipnote.dev/random.json"
    }

    /**
     * Hämtar en slumpmässig kaffebild från API:t och uppdaterar Compose-state.
     */
    fun loadRandomCoffeeImage() {
        loading.value = true
        error.value = null

        val request = JsonObjectRequest(
            Request.Method.GET, RANDOM_COFFEE_API_URL, null,
            { json ->
                // Lyckad förfrågan → spara bildens URL
                imageUrl.value = json.optString("file", null)
                loading.value = false
            },
            { err ->
                // Något gick fel → spara felmeddelande
                error.value = err.message ?: "Unknown Network Error"
                loading.value = false
            }
        )

        // Lägg till i Volley-kön för att skicka förfrågan
        queue.add(request)
    }
}
