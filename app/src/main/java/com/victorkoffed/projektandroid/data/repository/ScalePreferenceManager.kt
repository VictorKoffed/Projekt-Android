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
 * Manages the persistence of user preferences related to smart scale connectivity.
 * Isolates SharedPreferences I/O from the ViewModel and exposes observable state
 * for seamless integration with Compose UI.
 */
@Singleton
class ScalePreferenceManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val managerScope = CoroutineScope(Dispatchers.IO + Job())

    companion object {
        private const val PREFS_NAME = "ScalePrefs"
        private const val PREF_REMEMBERED_SCALE_ADDRESS = "remembered_scale_address"
        private const val PREF_REMEMBER_SCALE_ENABLED = "remember_scale_enabled"
        private const val PREF_AUTO_CONNECT_ENABLED = "auto_connect_enabled"
    }

    private val _rememberScaleEnabled = MutableStateFlow(false)
    val rememberScaleEnabled: StateFlow<Boolean> = _rememberScaleEnabled.asStateFlow()

    private val _autoConnectEnabled = MutableStateFlow(false)
    val autoConnectEnabled: StateFlow<Boolean> = _autoConnectEnabled.asStateFlow()

    private val _rememberedScaleAddress = MutableStateFlow<String?>(null)
    val rememberedScaleAddress: StateFlow<String?> = _rememberedScaleAddress.asStateFlow()

    init {
        managerScope.launch {
            val rememberEnabled = sharedPreferences.getBoolean(PREF_REMEMBER_SCALE_ENABLED, false)
            val autoConnect = sharedPreferences.getBoolean(PREF_AUTO_CONNECT_ENABLED, rememberEnabled)

            _rememberScaleEnabled.value = rememberEnabled
            _autoConnectEnabled.value = autoConnect
            _rememberedScaleAddress.value = loadRememberedScaleAddressInternal()
        }
    }

    /**
     * Toggles the hardware memory feature.
     * Enforces the business rule: disabling memory strictly invalidates and clears
     * both auto-connect configuration and the stored MAC address to prevent phantom reconnections.
     */
    fun setRememberScaleEnabled(enabled: Boolean) {
        managerScope.launch {
            sharedPreferences.edit { putBoolean(PREF_REMEMBER_SCALE_ENABLED, enabled) }
        }
        _rememberScaleEnabled.value = enabled

        if (!enabled) {
            setAutoConnectEnabled(false)
            setRememberedScaleAddress(null)
        }
    }

    /**
     * Toggles automatic BLE reconnection on startup.
     * Enforces the business rule: auto-connect is dependent on hardware memory being enabled.
     */
    fun setAutoConnectEnabled(enabled: Boolean) {
        val newValue = enabled && _rememberScaleEnabled.value
        if (_autoConnectEnabled.value != newValue) {
            managerScope.launch {
                sharedPreferences.edit { putBoolean(PREF_AUTO_CONNECT_ENABLED, newValue) }
            }
            _autoConnectEnabled.value = newValue
        }
    }

    fun setRememberedScaleAddress(address: String?) {
        managerScope.launch {
            if (address != null && _rememberScaleEnabled.value) {
                sharedPreferences.edit { putString(PREF_REMEMBERED_SCALE_ADDRESS, address) }
            } else {
                sharedPreferences.edit { remove(PREF_REMEMBERED_SCALE_ADDRESS) }
            }
            _rememberedScaleAddress.value = loadRememberedScaleAddressInternal()
        }
    }

    fun loadRememberedScaleAddress(): String? = _rememberedScaleAddress.value

    private fun loadRememberedScaleAddressInternal(): String? {
        return if (_rememberScaleEnabled.value) {
            sharedPreferences.getString(PREF_REMEMBERED_SCALE_ADDRESS, null)
        } else {
            null
        }
    }

    fun forgetScale() {
        setRememberScaleEnabled(false)
        setRememberedScaleAddress(null)
    }
}