package com.victorkoffed.projektandroid.data.repository

import android.content.Context
import com.victorkoffed.projektandroid.data.ble.BookooBleClient
import com.victorkoffed.projektandroid.domain.model.DiscoveredDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan

/**
 * Implementation av [ScaleRepository] som använder [BookooBleClient] för att skanna BLE-enheter.
 */
class BookooScaleRepositoryImpl(context: Context) : ScaleRepository {

    private val client = BookooBleClient(context)

    /**
     * Startar scanning via [BookooBleClient.startScan] och konverterar ScanResult till DiscoveredDevice.
     * Vi använder [scan]-operatorn för att bygga upp en växande lista utan dubbletter.
     */
    override fun startScanDevices(): Flow<List<DiscoveredDevice>> =
        client.startScan()
            .scan(emptyList<DiscoveredDevice>()) { currentList, result ->
                val newDevice = DiscoveredDevice(
                    name = result.device.name,
                    address = result.device.address,
                    rssi = result.rssi
                )

                // Om enheten redan finns, uppdatera RSSI, annars lägg till den
                val updated = currentList.toMutableList()
                val existingIndex = updated.indexOfFirst { it.address == newDevice.address }
                if (existingIndex != -1) {
                    updated[existingIndex] = newDevice
                } else {
                    updated.add(newDevice)
                }

                // Sortera starkaste signal överst
                updated.sortedByDescending { it.rssi }
            }
}
