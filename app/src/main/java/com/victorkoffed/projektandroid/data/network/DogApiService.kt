package com.victorkoffed.projektandroid.data.network

import android.content.Context
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley


object DogApiService {
    private const val RANDOM_DOG_URL = "https://dog.ceo/api/breeds/image/random"
    private var requestQueue: RequestQueue? = null

    private fun queue(context: Context): RequestQueue {
        if (requestQueue == null) {
            requestQueue = Volley.newRequestQueue(context.applicationContext)
        }
        return requestQueue!!
    }

    fun getRandomDogImage(context: Context, onResult: (String?) -> Unit) {
        val req = JsonObjectRequest(
            Request.Method.GET,
            RANDOM_DOG_URL,
            null,
            { res ->
                val url = res.optString("message")
                onResult(url.takeIf { it.isNotBlank() })
            },
            { err ->
                err.printStackTrace()
                onResult(null)
            }
        )
        queue(context).add(req)
    }
    fun getDogImageByBreedKey(context: Context, breedKey: String, onResult: (String?) -> Unit) {
        val parts = breedKey.split("-")
        val url = when (parts.size) {
            1 -> "https://dog.ceo/api/breed/${parts[0]}/images/random"
            2 -> "https://dog.ceo/api/breed/${parts[0]}/${parts[1]}/images/random"
            else -> "https://dog.ceo/api/breeds/image/random"
        }

        val req = JsonObjectRequest(
            Request.Method.GET,
            url,
            null,
            { res ->
                val img = res.optString("message")
                onResult(img.takeIf { it.isNotBlank() })
            },
            { err ->
                err.printStackTrace(); onResult(null)
            }
        )
        queue(context).add(req)
    }

}
