package com.victorkoffed.projektandroid

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Custom Application class serving as the dependency injection root
 * for Hilt components across the application lifecycle.
 */
@HiltAndroidApp
class CoffeeJournalApplication : Application()