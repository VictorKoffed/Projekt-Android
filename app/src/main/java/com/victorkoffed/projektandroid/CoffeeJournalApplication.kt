package com.victorkoffed.projektandroid

import android.app.Application
import com.victorkoffed.projektandroid.data.themePref.ThemePreferenceManager
import com.victorkoffed.projektandroid.data.db.CoffeeDatabase
import com.victorkoffed.projektandroid.data.repository.CoffeeRepository
import com.victorkoffed.projektandroid.data.repository.CoffeeRepositoryImpl

/**
 * Anpassad Application-klass som håller globala singleton-instanser för
 * databasen (CoffeeDatabase) och repository-lagret (CoffeeRepository).
 * Dessa instanser initieras lat.
 */
class CoffeeJournalApplication : Application() {

    // NY INSTANS: Theme Preference Manager (gjord tillgänglig för ViewModels)
    val themePreferenceManager by lazy {
        ThemePreferenceManager(applicationContext)
    }

    // Lat initiering av Room-databasen. Initieras först vid första access.
    val coffeeDatabase by lazy {
        CoffeeDatabase.getInstance(context = this)
    }

    // Lat initiering av CoffeeRepository-implementeringen, som använder DAO från databasen.
    val coffeeRepository: CoffeeRepository by lazy {
        CoffeeRepositoryImpl(coffeeDatabase.coffeeDao())
    }
}