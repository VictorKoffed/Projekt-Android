package com.victorkoffed.projektandroid.data.network

import android.content.Context
import com.android.volley.RequestQueue
import com.android.volley.toolbox.Volley

/**
 * Singleton-klass för att hantera Volley RequestQueue.
 * Säkerställer att endast en global nätverkskö används för hela applikationen,
 * vilket optimerar trådanvändning och resurser.
 */
class NetworkRequestQueue private constructor(context: Context) {
    /** Den faktiska Volley-kön, skapad med applikationskontext för livscykelhantering. */
    val queue: RequestQueue = Volley.newRequestQueue(context.applicationContext)

    companion object {
        // @Volatile garanterar att INSTANCE alltid läses från huvudminnet.
        @Volatile private var INSTANCE: NetworkRequestQueue? = null

        /**
         * Returnerar den singleton-instansen av NetworkRequestQueue.
         * Använder dubbelkontrollerad låsning (double-checked locking) för trådsäker instansiering.
         */
        fun getInstance(context: Context): NetworkRequestQueue =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: NetworkRequestQueue(context).also { INSTANCE = it }
            }
    }
}