package com.victorkoffed.projektandroid.domain.model

/**
 * Representerar de olika tillstånden en BLE-anslutning kan ha.
 * Används för att informera UI:t om vad som händer.
 */
sealed class BleConnectionState {
    object Disconnected : BleConnectionState()
    object Connecting : BleConnectionState()
    data class Connected(val deviceName: String) : BleConnectionState()
    data class Error(val message: String) : BleConnectionState()
}
