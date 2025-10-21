package com.victorkoffed.projektandroid.data.ble

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.UUID

/**
 * Låg-nivå BLE-klient för Bookoo Mini Scale.
 * Steg 1: Endast scanning efter enheter som annonserar service 0x0FFE.
 *
 * Service UUID (16-bit): 0x0FFE  → 00000FFE-0000-1000-8000-00805F9B34FB
 * Weight Char (notify):  0xFF11
 * Command Char (write):  0xFF12
 */
class BookooBleClient(private val context: Context) {

    private val btManager: BluetoothManager by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }
    private val btAdapter by lazy { btManager.adapter }
    private val scanner: BluetoothLeScanner? by lazy { btAdapter?.bluetoothLeScanner }

    /**
     * Startar en BLE-skanning och emittar ScanResult för varje funnen enhet.
     * Flödet avbryts genom att cancella Coroutine-scopet som samlar in från flödet.
     *
     * @return Ett Flow som emittar [ScanResult]-objekt.
     * @throws IllegalStateException om Bluetooth inte är tillgängligt eller påslaget.
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startScan(): Flow<ScanResult> = callbackFlow {
        val localScanner = scanner ?: run {
            close(IllegalStateException("Bluetooth adapter is not available or turned off."))
            return@callbackFlow
        }

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                // Försök att skicka resultatet. Om mottagaren har avbrutit,
                // kommer detta inte att lyckas, men det hanteras av flödet.
                trySend(result)
            }

            override fun onScanFailed(errorCode: Int) {
                // Skicka ett fel om skanningen misslyckas, vilket avslutar flödet för mottagaren.
                close(IllegalStateException("BLE scan failed with error code: $errorCode"))
            }
        }

        val filters = listOf(
            // Filtrera på Bookoo-servicen för att endast se relevanta enheter.
            ScanFilter.Builder().setServiceUuid(BOOKOO_SERVICE_PARCEL_UUID).build()
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        // Nu vet vi att 'localScanner' inte är null.
        localScanner.startScan(filters, settings, callback)

        // Denna block körs när flödet cancelleras (t.ex. när collectorn slutar lyssna).
        awaitClose {
            // Stoppa skanningen för att spara batteri.
            // En extra null-kontroll skadar inte, även om den är osannolik här.
            localScanner.stopScan(callback)
        }
    }

    companion object {
        /** Service UUID för Bookoo Mini Scale. */
        val BOOKOO_SERVICE_UUID: UUID = UUID.fromString("00000FFE-0000-1000-8000-00805F9B34FB")

        private val BOOKOO_SERVICE_PARCEL_UUID = ParcelUuid(BOOKOO_SERVICE_UUID)
    }
}
