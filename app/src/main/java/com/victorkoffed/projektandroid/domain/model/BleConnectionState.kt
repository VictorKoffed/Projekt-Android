package com.victorkoffed.projektandroid.domain.model

/**
 * Represents the lifecycle states of a Bluetooth Low Energy (BLE) connection.
 * Acts as the primary state machine contract between the hardware communication layer
 * and the UI, encapsulating connection transitions and device metadata.
 */
sealed class BleConnectionState {

    object Disconnected : BleConnectionState()

    /**
     * Indicates an ongoing connection attempt.
     * Represents the transitional state while pending GATT service discovery
     * and platform-specific callback resolutions.
     */
    object Connecting : BleConnectionState()

    data class Connected(
        val deviceName: String,
        val deviceAddress: String,
        val batteryPercent: Int? = null
    ) : BleConnectionState()

    data class Error(val message: String) : BleConnectionState()
}