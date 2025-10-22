package com.victorkoffed.projektandroid

import android.app.Application
import com.victorkoffed.projektandroid.data.db.CoffeeDatabase
import com.victorkoffed.projektandroid.data.repository.CoffeeRepository
import com.victorkoffed.projektandroid.data.repository.CoffeeRepositoryImpl

/**
 * En anpassad Application-klass för att hålla globala instanser,
 * som vår databas och repository.
 */
class CoffeeJournalApplication : Application() {

    val coffeeDatabase by lazy {
        CoffeeDatabase.getInstance(context = this)
    }

    val coffeeRepository: CoffeeRepository by lazy {
        CoffeeRepositoryImpl(coffeeDatabase.coffeeDao())
    }
}
