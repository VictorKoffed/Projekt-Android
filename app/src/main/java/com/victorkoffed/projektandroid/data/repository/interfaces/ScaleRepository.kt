package com.victorkoffed.projektandroid.data.repository.interfaces

import com.victorkoffed.projektandroid.domain.model.BleConnectionState
import com.victorkoffed.projektandroid.domain.model.DiscoveredDevice
import com.victorkoffed.projektandroid.domain.model.ScaleMeasurement
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Defines the contract for hardware integration with smart coffee scales via BLE.
 * Abstracts protocol-specifics to provide a unified API for real-time telemetry,
 * connection lifecycle management, and hardware command orchestration.
 */
interface ScaleRepository {

    fun startScanDevices(): Flow<List<DiscoveredDevice>>

    fun connect(address: String)

    fun disconnect()

    /**
     * Emits real-time scale measurements.
     * Implementations must ensure emitted values are software-adjusted to account for local taring offsets,
     * providing immediate UI feedback (a zeroed state) without waiting for a hardware round-trip response.
     */
    fun observeMeasurements(): StateFlow<ScaleMeasurement>

    fun observeConnectionState(): StateFlow<BleConnectionState>

    fun tareScale()

    fun tareScaleAndStartTimer()

    fun stopTimer()

    fun resetTimer()

    fun startTimer()
}