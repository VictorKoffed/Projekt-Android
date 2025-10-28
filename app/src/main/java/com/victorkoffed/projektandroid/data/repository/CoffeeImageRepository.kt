package com.victorkoffed.projektandroid.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository för att hämta slumpmässiga kaffebilder från ett externt API.
 * Använder en modern Coroutine/blocking I/O-metod i stället för Volley.
 * Hilt injicerar denna klass som en Singleton.
 */
interface CoffeeImageRepository {
    /**
     * Hämtar URL:en till en slumpmässig kaffebild.
     * @return URL:en till bilden som String, eller null om anropet misslyckades.
     */
    suspend fun fetchRandomCoffeeImageUrl(): String?
}

@Singleton
class CoffeeImageRepositoryImpl @Inject constructor() : CoffeeImageRepository {

    companion object {
        // Den API-URL som används för att hämta en slumpmässig kaffebild.
        private const val RANDOM_COFFEE_API_URL = "https://coffee.alexflipnote.dev/random.json"
        private const val TAG = "CoffeeImageRepo"
    }

    override suspend fun fetchRandomCoffeeImageUrl(): String? = withContext(Dispatchers.IO) {
        try {
            // Använd URL().readText() blockerar tråden men körs i Dispatchers.IO
            val jsonString = URL(RANDOM_COFFEE_API_URL).readText()

            // Parsar JSON-svaret
            val json = JSONObject(jsonString)
            val fileUrl = json.optString("file")

            if (fileUrl.isNullOrBlank()) {
                Log.w(TAG, "API call successful but 'file' URL was missing or empty.")
                null
            } else {
                fileUrl
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch coffee image URL: ${e.message}", e)
            // Kasta om felet för att ViewMoelel ska kunna fånga det och sätta felstatus.
            throw e
        }
    }
}