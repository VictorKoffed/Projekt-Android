package com.victorkoffed.projektandroid.data.repository

import com.victorkoffed.projektandroid.domain.model.BleConnectionState
import com.victorkoffed.projektandroid.domain.model.DiscoveredDevice
import com.victorkoffed.projektandroid.domain.model.ScaleMeasurement
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Abstraktion för våg/ble-data.
 */
interface ScaleRepository {
    fun startScanDevices(): Flow<List<DiscoveredDevice>>
    fun connect(address: String)
    fun disconnect()
    fun observeMeasurements(): Flow<ScaleMeasurement>
    fun observeConnectionState(): StateFlow<BleConnectionState>

    /** Nollställer (tarerar) vågen. */
    fun tareScale()
}
