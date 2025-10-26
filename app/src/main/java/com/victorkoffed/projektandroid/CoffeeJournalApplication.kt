package com.victorkoffed.projektandroid

import android.app.Application
import dagger.hilt.android.HiltAndroidApp // <-- NY IMPORT

/**
 * Anpassad Application-klass som håller globala singleton-instanser för
 * databasen (CoffeeDatabase) och repository-lagret (CoffeeRepository).
 * Dessa instanser initieras lat.
 *
 * Hilt kommer att hantera beroendeinjektionen för de flesta instanser.
 */
@HiltAndroidApp // <-- NY ANNOTERING
class CoffeeJournalApplication : Application()