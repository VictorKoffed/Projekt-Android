package com.victorkoffed.projektandroid.ui.viewmodel.coffee

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.victorkoffed.projektandroid.data.network.NetworkRequestQueue

/**
 * ViewModel för att hantera logiken kring att hämta en slumpmässig kaffebild från ett externt API.
 * Använder Volley för nätverksanrop och håller state som Compose-UI:et observerar.
 */
class CoffeeImageViewModel(app: Application) : AndroidViewModel(app) {

    // State för den slumpmässigt hämtade bildens URL.
    val imageUrl = mutableStateOf<String?>(null)
    // Indikerar om en nätverksförfrågan pågår.
    val loading = mutableStateOf(false)
    // Håller ett eventuellt felmeddelande från nätverksanropet.
    val error = mutableStateOf<String?>(null)

    // Skapar en referens till den globala nätverkskön (Volley Singleton).
    private val queue = NetworkRequestQueue.Companion.getInstance(app).queue

    companion object {
        // Den API-URL som används för att hämta en slumpmässig kaffebild.
        private const val RANDOM_COFFEE_API_URL = "https://coffee.alexflipnote.dev/random.json"
    }

    /**
     * Utför ett nätverksanrop för att hämta URL:en till en slumpmässig kaffebild från API:t
     * och uppdaterar det observerbara Compose-state:t.
     */
    fun loadRandomCoffeeImage() {
        loading.value = true
        error.value = null

        val request = JsonObjectRequest(
            Request.Method.GET, RANDOM_COFFEE_API_URL, null,
            { json ->
                // Lyckad förfrågan → spara bildens URL, nyckeln är "file" i JSON-svaret.
                imageUrl.value = json.optString("file")
                loading.value = false
            },
            { err ->
                // Något gick fel under nätverksanropet → spara felmeddelande.
                error.value = err.message ?: "Okänt nätverksfel" // Översatte felmeddelandet.
                loading.value = false
            }
        )

        // Lägg till förfrågan i Volley-kön för att starta anropet.
        queue.add(request)
    }

    /**
     * Nollställer felmeddelandet i state:t efter att det har presenterats för användaren.
     */
    fun clearError() {
        error.value = null
    }
}