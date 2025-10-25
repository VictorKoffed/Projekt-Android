package com.victorkoffed.projektandroid.domain.model

/**
 * Representerar de olika tillstånden en BLE-anslutning kan ha.
 * Används för att informera UI:t om vad som händer med anslutningen
 * och bära med sig relevant data, som enhetens namn.
 */
sealed class BleConnectionState {
    /** Inget aktivt försök till anslutning. Standardläge. */
    object Disconnected : BleConnectionState()

    /** Anslutningsförsök pågår, väntar på GATT-callback. */
    object Connecting : BleConnectionState()

    /** Anslutning etablerad och tjänster har upptäckts. */
    data class Connected(val deviceName: String) : BleConnectionState()

    /** Ett fel uppstod under skanning, anslutning eller tjänstupptäckt. */
    data class Error(val message: String) : BleConnectionState()
}