package com.victorkoffed.projektandroid.data.repository

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
 * Hanterar lagring och läsning av vågrelaterade inställningar (minns våg, auto-connect)
 * med hjälp av SharedPreferences.
 *
 * Denna manager avlastar ScaleViewModel från direkt hantering av beständighetslogik.
 *
 * @property context Applikationskontext injicerad av Hilt.
 */
@Singleton
class ScalePreferenceManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "ScalePrefs"
        private const val PREF_REMEMBERED_SCALE_ADDRESS = "remembered_scale_address"
        private const val PREF_REMEMBER_SCALE_ENABLED = "remember_scale_enabled"
        private const val PREF_AUTO_CONNECT_ENABLED = "auto_connect_enabled"
    }

    // --- StateFlows för observerbarhet ---
    private val _rememberScaleEnabled = MutableStateFlow(
        sharedPreferences.getBoolean(PREF_REMEMBER_SCALE_ENABLED, false)
    )
    val rememberScaleEnabled: StateFlow<Boolean> = _rememberScaleEnabled.asStateFlow()

    private val _autoConnectEnabled = MutableStateFlow(
        sharedPreferences.getBoolean(PREF_AUTO_CONNECT_ENABLED, _rememberScaleEnabled.value)
    )
    val autoConnectEnabled: StateFlow<Boolean> = _autoConnectEnabled.asStateFlow()

    private val _rememberedScaleAddress = MutableStateFlow(
        loadRememberedScaleAddressInternal()
    )
    val rememberedScaleAddress: StateFlow<String?> = _rememberedScaleAddress.asStateFlow()

    // --- Publik skrivåtkomst ---

    /**
     * Sätter status för om vågen ska kommas ihåg.
     * Om false, nollställs även auto-connect och den sparade adressen.
     */
    fun setRememberScaleEnabled(enabled: Boolean) {
        sharedPreferences.edit { putBoolean(PREF_REMEMBER_SCALE_ENABLED, enabled) }
        _rememberScaleEnabled.value = enabled

        if (!enabled) {
            // Om remember stängs av, stängs även auto-connect av och adressen glöms.
            setAutoConnectEnabled(false)
            setRememberedScaleAddress(null)
        }
    }

    /**
     * Sätter status för auto-connect.
     * Observera: Auto-connect kan endast vara aktivt om 'rememberScaleEnabled' är sant.
     */
    fun setAutoConnectEnabled(enabled: Boolean) {
        val newValue = enabled && _rememberScaleEnabled.value
        if (_autoConnectEnabled.value != newValue) {
            sharedPreferences.edit { putBoolean(PREF_AUTO_CONNECT_ENABLED, newValue) }
            _autoConnectEnabled.value = newValue
        }
    }

    /**
     * Sparar adressen om remember är aktiverat, annars raderas den.
     */
    fun setRememberedScaleAddress(address: String?) {
        if (address != null && _rememberScaleEnabled.value) {
            sharedPreferences.edit { putString(PREF_REMEMBERED_SCALE_ADDRESS, address) }
        } else {
            sharedPreferences.edit { remove(PREF_REMEMBERED_SCALE_ADDRESS) }
        }
        // Uppdatera stateflow genom att läsa från persistence
        _rememberedScaleAddress.value = loadRememberedScaleAddressInternal()
    }

    /**
     * Läser den sparade adressen endast om remember är aktiverat.
     */
    fun loadRememberedScaleAddress(): String? = _rememberedScaleAddress.value

    /**
     * Intern funktion för att läsa adressen direkt från SharedPreferences baserat på aktuell state.
     */
    private fun loadRememberedScaleAddressInternal(): String? {
        // Kontrollera om "remember" är på enligt vår interna stateFlow, annars läs direkt från prefs.
        return if (_rememberScaleEnabled.value) {
            sharedPreferences.getString(PREF_REMEMBERED_SCALE_ADDRESS, null)
        } else {
            null
        }
    }

    /** Nollställer alla sparade våginställningar. */
    fun forgetScale() {
        setRememberScaleEnabled(false)
        setRememberedScaleAddress(null)
    }
}