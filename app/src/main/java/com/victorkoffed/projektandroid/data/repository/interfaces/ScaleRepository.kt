package com.victorkoffed.projektandroid.data.repository.interfaces

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

    /** * Ger en Flow med kontinuerliga, TARER-JUSTERADE mätvärden (vikt/flöde/tid)
     * från den anslutna vågen.
     */
    fun observeMeasurements(): StateFlow<ScaleMeasurement> // Ändrad till StateFlow

    /** Ger en StateFlow som reflekterar den aktuella anslutningsstatusen (Disconnected, Connecting, Connected, Error). */
    fun observeConnectionState(): StateFlow<BleConnectionState>

    /** Skickar kommandot för att nollställa (tarera) vågens mätvärde (0x01). */
    fun tareScale()

    /** Skickar kommandot för att nollställa (tarera) OCH starta vågens interna timer (0x07). */
    fun tareScaleAndStartTimer()

    /** Skickar kommandot för att stoppa vågens interna timer (0x05). */
    fun stopTimer()

    /** Skickar kommandot för att nollställa vågens interna timer (0x06). */
    fun resetTimer()

    /** Skickar kommandot för att starta vågens interna timer (0x04). */
    fun startTimer()
}