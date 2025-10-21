package com.victorkoffed.projektandroid.domain.model

/**
 * Representerar en hittad BLE-enhet under scanning.
 * - name kan vara null om enheten inte annonserar namn.
 * - address är MAC-adressen (unik identifierare).
 * - rssi är signalstyrkan (högre = närmare, typiskt negativt värde).
 */
data class DiscoveredDevice(
    val name: String?,
    val address: String,
    val rssi: Int
)
