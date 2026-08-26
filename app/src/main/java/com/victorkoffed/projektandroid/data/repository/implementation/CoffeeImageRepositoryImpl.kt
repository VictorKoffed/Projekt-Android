/**
 * Implementation Note: Asynchronous network I/O and JSON parsing logic
 * were structured with AI assistance. See README.md.
 */

package com.victorkoffed.projektandroid.data.repository.implementation

import android.util.Log
import com.victorkoffed.projektandroid.data.repository.interfaces.CoffeeImageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Concrete implementation of [CoffeeImageRepository].
 * Responsible for interfacing with external image APIs to fetch placeholder imagery
 * for brew sessions lacking user-provided photos.
 */
@Singleton
class CoffeeImageRepositoryImpl @Inject constructor() : CoffeeImageRepository {

    companion object {
        private const val RANDOM_COFFEE_API_URL = "https://coffee.alexflipnote.dev/random.json"
        private const val TAG = "CoffeeImageRepo"
    }

    override suspend fun fetchRandomCoffeeImageUrl(): String? = withContext(Dispatchers.IO) {
        try {
            val jsonString = URL(RANDOM_COFFEE_API_URL).readText()
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
            // Propagate the exception upstream to allow the ViewModel to resolve the UI error state.
            throw e
        }
    }
}