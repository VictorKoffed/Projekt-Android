package com.victorkoffed.projektandroid.domain.model

/**
 * Representerar en hittad BLE-enhet under skanning.
 *
 * @property name Enhetens annonserade namn (kan vara null).
 * @property address Enhetens unika MAC-adress (identifierare).
 * @property rssi Signalstyrka i dBm (högre, dvs. närmare noll, betyder starkare signal).
 */
data class DiscoveredDevice(
    val name: String?,
    val address: String,
    val rssi: Int
)