package com.victorkoffed.projektandroid.data.repository

import com.victorkoffed.projektandroid.domain.model.BleConnectionState
import com.victorkoffed.projektandroid.domain.model.DiscoveredDevice
import com.victorkoffed.projektandroid.domain.model.ScaleMeasurement
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Kontraktet (interfacet) för att hantera all kommunikation med BLE-vågen.
 * Abstraherar bort den underliggande BLE-implementeringen.
 */
interface ScaleRepository {
    /** Startar BLE-skanning och skickar kontinuerligt ut en uppdaterad lista över upptäckta enheter. */
    fun startScanDevices(): Flow<List<DiscoveredDevice>>

    /** Initierar anslutning till enheten med den givna BLE-adressen. */
    fun connect(address: String)

    /** Stänger den nuvarande BLE-anslutningen. */
    fun disconnect()

    /** Ger en Flow med kontinuerliga mätvärden (vikt/flöde) från den anslutna vågen. */
    fun observeMeasurements(): Flow<ScaleMeasurement>

    /** Ger en StateFlow som reflekterar den aktuella anslutningsstatusen (Disconnected, Connecting, Connected, Error). */
    fun observeConnectionState(): StateFlow<BleConnectionState>

    /** Skickar kommandot för att nollställa (tarera) vågens mätvärde. */
    fun tareScale()
}