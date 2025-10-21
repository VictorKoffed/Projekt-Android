package com.victorkoffed.projektandroid.data.repository

import kotlinx.coroutines.flow.Flow
import com.victorkoffed.projektandroid.domain.model.DiscoveredDevice

/**
 * Abstraktion för våg/ble-data.
 * Steg 1: bara scanning av enheter som annonserar Bookoo-servicen.
 */
interface ScaleRepository {

    /**
     * Startar scanning och emittar löpande listor av hittade enheter.
     * Listan är deduplicerad per MAC-adress och sorterad på starkast signal (rssi desc).
     */
    fun startScanDevices(): Flow<List<DiscoveredDevice>>
}
