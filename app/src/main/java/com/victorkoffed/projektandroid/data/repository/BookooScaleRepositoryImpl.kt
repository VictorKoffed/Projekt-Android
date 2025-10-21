package com.victorkoffed.projektandroid.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.victorkoffed.projektandroid.data.ble.BookooBleClient
import com.victorkoffed.projektandroid.domain.model.BleConnectionState
import com.victorkoffed.projektandroid.domain.model.DiscoveredDevice
import com.victorkoffed.projektandroid.domain.model.ScaleMeasurement
import kotlinx.coroutines.flow.*

/**
 * Implementation av [ScaleRepository] som använder [BookooBleClient].
 */
class BookooScaleRepositoryImpl(private val context: Context) : ScaleRepository {

    private val client = BookooBleClient(context)

    override fun startScanDevices(): Flow<List<DiscoveredDevice>> {
        // Kontrollera rättigheter innan skanning
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            return flow { throw SecurityException("Missing BLUETOOTH_SCAN permission.") }
        }
        return client.startScan()
            .map { result ->
                DiscoveredDevice(
                    name = result.device.name,
                    address = result.device.address,
                    rssi = result.rssi
                )
            }
            .scan(emptyList<DiscoveredDevice>()) { acc, newDevice ->
                val mutable = acc.toMutableList()
                val existing = mutable.find { it.address == newDevice.address }
                if (existing != null) {
                    val index = mutable.indexOf(existing)
                    mutable[index] = newDevice
                } else {
                    mutable.add(newDevice)
                }
                mutable.sortedByDescending { it.rssi }
            }
    }

    override fun connect(address: String) = client.connect(address)
    override fun disconnect() = client.disconnect()
    override fun observeMeasurements(): Flow<ScaleMeasurement> = client.measurements
    override fun observeConnectionState(): StateFlow<BleConnectionState> = client.connectionState

    /** Implementation för att anropa tare-kommandot i BLE-klienten. */
    override fun tareScale() {
        client.sendTareCommand()
    }
}

