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
 * Ansvarar för att kontrollera behörigheter och transformera råa BLE-data till domänmodeller.
 */
@Singleton
class BookooScaleRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : ScaleRepository {

    // Initieras lazily så att Context finns när den skapas via Hilt
    private val client: BookooBleClient by lazy { BookooBleClient(context) }

    /**
     * Startar BLE-skanning och samlar skanningsresultat till en Flow<List<DiscoveredDevice>>.
     * Denna metod hanterar även dynamisk uppdatering av enheter som redan upptäckts.
     */
    override fun startScanDevices(): Flow<List<DiscoveredDevice>> {
        // Kontrollera nödvändig behörighet (BLUETOOTH_SCAN) innan skanning initieras.
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return flow { throw SecurityException("Missing BLUETOOTH_SCAN permission.") }
        }

        return client.startScan()
            // Steg 1: Konvertera råa ScanResult till domänmodellen DiscoveredDevice.
            .map { result ->
                DiscoveredDevice(
                    name = result.device.name,
                    address = result.device.address,
                    rssi = result.rssi
                )
            }
            // Steg 2: Bygg en växande lista med unika enheter och uppdatera RSSI vid dubblett.
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

    /** Skickar tare/nollställningskommandot till den anslutna vågen via BLE-klienten. */
    override fun tareScale() {
        client.sendTareCommand()
    }
}
