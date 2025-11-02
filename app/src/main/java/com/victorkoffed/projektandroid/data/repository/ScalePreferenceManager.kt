package com.victorkoffed.projektandroid.data.repository

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

    // Skapa en privat scope för denna manager som kör på IO-tråden
    private val managerScope = CoroutineScope(Dispatchers.IO + Job())

    companion object {
        private const val PREFS_NAME = "ScalePrefs"
        private const val PREF_REMEMBERED_SCALE_ADDRESS = "remembered_scale_address"
        private const val PREF_REMEMBER_SCALE_ENABLED = "remember_scale_enabled"
        private const val PREF_AUTO_CONNECT_ENABLED = "auto_connect_enabled"
    }

    // --- StateFlows för observerbarhet ---

    // 1. Initiera med standardvärden (icke-blockerande)
    private val _rememberScaleEnabled = MutableStateFlow(false)
    val rememberScaleEnabled: StateFlow<Boolean> = _rememberScaleEnabled.asStateFlow()

    private val _autoConnectEnabled = MutableStateFlow(false)
    val autoConnectEnabled: StateFlow<Boolean> = _autoConnectEnabled.asStateFlow()

    private val _rememberedScaleAddress = MutableStateFlow<String?>(null)
    val rememberedScaleAddress: StateFlow<String?> = _rememberedScaleAddress.asStateFlow()

    // 2. Ladda de riktiga värdena asynkront i bakgrunden
    init {
        managerScope.launch {
            // Läs från SharedPreferences på en IO-tråd
            val rememberEnabled = sharedPreferences.getBoolean(PREF_REMEMBER_SCALE_ENABLED, false)
            val autoConnect = sharedPreferences.getBoolean(PREF_AUTO_CONNECT_ENABLED, rememberEnabled)

            // Uppdatera StateFlows (vilket kommer att meddela observers på main-tråden)
            _rememberScaleEnabled.value = rememberEnabled
            _autoConnectEnabled.value = autoConnect
            _rememberedScaleAddress.value = loadRememberedScaleAddressInternal() // Denna är nu beroende av _rememberScaleEnabled.value
        }
    }


    // --- Publik skrivåtkomst ---

    /**
     * Sätter status för om vågen ska kommas ihåg.
     * Om false, nollställs även auto-connect och den sparade adressen.
     */
    fun setRememberScaleEnabled(enabled: Boolean) {
        // Kör disk-skrivning på IO-tråden
        managerScope.launch {
            sharedPreferences.edit { putBoolean(PREF_REMEMBER_SCALE_ENABLED, enabled) }
        }
        _rememberScaleEnabled.value = enabled // Uppdatera state omedelbart

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
            // Kör disk-skrivning på IO-tråden
            managerScope.launch {
                sharedPreferences.edit { putBoolean(PREF_AUTO_CONNECT_ENABLED, newValue) }
            }
            _autoConnectEnabled.value = newValue // Uppdatera state omedelbart
        }
    }

    /**
     * Sparar adressen om remember är aktiverat, annars raderas den.
     */
    fun setRememberedScaleAddress(address: String?) {
        // Kör disk-skrivning på IO-tråden
        managerScope.launch {
            if (address != null && _rememberScaleEnabled.value) {
                sharedPreferences.edit { putString(PREF_REMEMBERED_SCALE_ADDRESS, address) }
            } else {
                sharedPreferences.edit { remove(PREF_REMEMBERED_SCALE_ADDRESS) }
            }
            // Uppdatera stateflow genom att läsa från persistence (trådsäkert)
            _rememberedScaleAddress.value = loadRememberedScaleAddressInternal()
        }
    }

    /**
     * Läser den sparade adressen endast om remember är aktiverat.
     */
    fun loadRememberedScaleAddress(): String? = _rememberedScaleAddress.value

    /**
     * Intern funktion för att läsa adressen direkt från SharedPreferences baserat på aktuell state.
     * Denna MÅSTE anropas från en IO-tråd (vilket den görs från init och setRememberedScaleAddress).
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