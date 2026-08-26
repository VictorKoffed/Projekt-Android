package com.victorkoffed.projektandroid.domain.model

/**
 * Represents a Bluetooth Low Energy (BLE) device identified during a hardware scan.
 * Acts as a lightweight data transfer object (DTO) to relay device availability
 * and signal strength (RSSI) to the UI layer for proximity-based sorting and connection selection.
 */
data class DiscoveredDevice(
    val name: String?,
    val address: String,
    val rssi: Int
)