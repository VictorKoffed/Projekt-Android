package com.victorkoffed.projektandroid.data.themePref

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages user preferences for the application theme (dark/light mode).
 * Isolates persistence I/O and exposes an observable state to allow
 * the Compose UI to react seamlessly to manual theme overrides across sessions.
 */
@Singleton
class ThemePreferenceManager @Inject constructor(
    @ApplicationContext context: Context
) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)

    private val managerScope = CoroutineScope(Dispatchers.IO + Job())

    companion object {
        private const val MANUAL_DARK_MODE_KEY = "manual_dark_mode"
        private const val DEFAULT_DARK_MODE = false
    }

    private val _isDarkMode = MutableStateFlow(DEFAULT_DARK_MODE)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    init {
        managerScope.launch {
            _isDarkMode.value = sharedPreferences.getBoolean(MANUAL_DARK_MODE_KEY, DEFAULT_DARK_MODE)
        }
    }

    /**
     * Updates the user's manual theme preference.
     * Mutates the state immediately to ensure a responsive UI, while delegating
     * the slower disk persistence operation to a background thread to prevent framing drops.
     */
    fun setManualDarkMode(isDark: Boolean) {
        managerScope.launch {
            sharedPreferences.edit {
                putBoolean(MANUAL_DARK_MODE_KEY, isDark)
            }
        }
        _isDarkMode.value = isDark
    }
}