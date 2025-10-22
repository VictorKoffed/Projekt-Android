package com.victorkoffed.projektandroid

import android.app.Application
import com.victorkoffed.projektandroid.data.repository.CoffeeRepository // Importera interfacet
import com.victorkoffed.projektandroid.data.repository.CoffeeRepositoryImpl // Importera implementationen explicit

/**
 * En anpassad Application-klass för att hålla globala instanser,
 * som vår databas och repository.
 */
class CoffeeJournalApplication : Application() {
    // Vi skapar instanserna "lazy" (när de först behövs)
    val coffeeDatabase by lazy {
        com.victorkoffed.projektandroid.data.db.CoffeeDatabase.getInstance(this)
    }

    // Explicit typ : CoffeeRepository
    val coffeeRepository: CoffeeRepository by lazy {
        // Använd den importerade klassen
        CoffeeRepositoryImpl(coffeeDatabase.coffeeDao())
    }
}

