package com.victorkoffed.projektandroid.data.network

import android.content.Context
import com.android.volley.RequestQueue
import com.android.volley.toolbox.Volley

/**
 * Singleton som skapar och tillhandahåller en global Volley RequestQueue.
 * Används för att skicka nätverksförfrågningar effektivt utan att skapa flera köer.
 */

class NetworkRequestQueue private constructor(context: Context) {
    val queue: RequestQueue = Volley.newRequestQueue(context.applicationContext)

    companion object {
        @Volatile private var INSTANCE: NetworkRequestQueue? = null
        fun getInstance(context: Context): NetworkRequestQueue =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: NetworkRequestQueue(context).also { INSTANCE = it }
            }
    }
}