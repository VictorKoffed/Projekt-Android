package com.victorkoffed.projektandroid.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.victorkoffed.projektandroid.data.ble.BookooBleClient
import com.victorkoffed.projektandroid.domain.model.BleConnectionState
import com.victorkoffed.projektandroid.domain.model.DiscoveredDevice
import com.victorkoffed.projektandroid.domain.model.ScaleMeasurement
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan

/**
 * Implementation av [ScaleRepository] som använder [BookooBleClient] för BLE-kommunikation.
 */
@Singleton
class BookooScaleRepositoryImpl @Inject constructor(
    @param:ApplicationContext @field:ApplicationContext private val context: Context
) : ScaleRepository {

    private val client: BookooBleClient by lazy { BookooBleClient(context) }

    override fun startScanDevices(): Flow<List<DiscoveredDevice>> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            return flow { throw SecurityException("Missing BLUETOOTH_SCAN permission.") }
        }

        return client.startScan()
            .map { result ->
                DiscoveredDevice(
                    name = result.device.name ?: result.device.address, // Använd adress som fallback
                    address = result.device.address,
                    rssi = result.rssi
                )
            }
            .scan(emptyList()) { acc, newDevice ->
                val mutable = acc.toMutableList()
                val idx = mutable.indexOfFirst { it.address == newDevice.address }
                if (idx >= 0) mutable[idx] = newDevice else mutable.add(newDevice)
                mutable.sortedByDescending { it.rssi }
            }
    }

    override fun connect(address: String) = client.connect(address)
    override fun disconnect() = client.disconnect()
    override fun observeMeasurements(): Flow<ScaleMeasurement> = client.measurements
    override fun observeConnectionState(): StateFlow<BleConnectionState> = client.connectionState

    /** Skickar tare/nollställningskommandot (0x01). */
    override fun tareScale() {
        client.sendTareCommand()
    }

    /** Skickar tare OCH start timer-kommandot (0x07). */
    override fun tareScaleAndStartTimer() {
        client.sendTareAndStartTimerCommand()
    }

    /** Skickar stopp-timer kommandot (0x05). */
    override fun stopTimer() {
        client.sendStopTimerCommand()
    }

    /** Skickar reset-timer kommandot (0x06). */
    override fun resetTimer() {
        client.sendResetTimerCommand()
    }

    /** Skickar start-timer kommandot (0x04). */
    override fun startTimer() {
        client.sendStartTimerCommand()
    }
}