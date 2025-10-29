package com.victorkoffed.projektandroid.data.themePref

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hanterar lagring och läsning av användarens manuella val av mörkt/ljust läge.
 * Använder SharedPreferences för enkel beständighet och StateFlow för att Compose ska kunna observera ändringar.
 */
@Singleton
class ThemePreferenceManager @Inject constructor(
    @ApplicationContext context: Context
) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)

    companion object {
        // Nyckel för att lagra mörkt läge: True = Dark, False = Light.
        private const val MANUAL_DARK_MODE_KEY = "manual_dark_mode"
        // Standardinställning (Light mode) om inget har sparats.
        private const val DEFAULT_DARK_MODE = false
    }

    // StateFlow som håller det aktuella läget. Värdet uppdateras vid ändringar och kan observeras av Compose.
    private val _isDarkMode = MutableStateFlow(
        sharedPreferences.getBoolean(MANUAL_DARK_MODE_KEY, DEFAULT_DARK_MODE)
    )
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    /**
     * Sparar användarens manuella val och uppdaterar StateFlow.
     * @param isDark True för mörkt läge, False för ljust läge.
     */
    fun setManualDarkMode(isDark: Boolean) {
        sharedPreferences.edit {
            putBoolean(MANUAL_DARK_MODE_KEY, isDark)
        }
        _isDarkMode.value = isDark // Uppdatera flow omedelbart
    }
}
