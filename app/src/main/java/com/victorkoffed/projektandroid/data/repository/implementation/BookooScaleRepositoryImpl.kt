/*
 * EXTERNT PROTOKOLL: Denna repository implementerar affärslogik (t.ex. tarering och kommandon)
 * baserat på Bookoo BLE Protocol.
 * Källa: https://github.com/BooKooCode/OpenSource/blob/main/bookoo_mini_scale/protocols.md
 *
 * Referensnotering (AI-assistans): Implementationen av Coroutine Flows och StateFlows
 * för att hantera vågdata har strukturerats med AI-assistans. Se README.md.
 */

package com.victorkoffed.projektandroid.data.repository.implementation

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.victorkoffed.projektandroid.data.ble.BookooBleClient
import com.victorkoffed.projektandroid.data.repository.interfaces.ScaleRepository
import com.victorkoffed.projektandroid.domain.model.BleConnectionState
import com.victorkoffed.projektandroid.domain.model.DiscoveredDevice
import com.victorkoffed.projektandroid.domain.model.ScaleMeasurement
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Implementation av [ScaleRepository] som använder [BookooBleClient] för BLE-kommunikation.
 * Denna klass hanterar nu även tareringslogiken.
 */
@Singleton
class BookooScaleRepositoryImpl @Inject constructor(
    @param:ApplicationContext @field:ApplicationContext private val context: Context
) : ScaleRepository {

    private val client: BookooBleClient by lazy { BookooBleClient(context) }
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    // --- State för Tarering ---
    private val _rawMeasurement = MutableStateFlow(ScaleMeasurement(0.0f, 0.0f))
    private val _tareOffset = MutableStateFlow(0.0f)

    /** Kombinerat Flöde för aktuell vikt och flöde (justerat för tarering). */
    private val _adjustedMeasurement: StateFlow<ScaleMeasurement> =
        combine(_rawMeasurement, _tareOffset) { raw, offset ->
            ScaleMeasurement(
                weightGrams = raw.weightGrams - offset,
                flowRateGramsPerSecond = raw.flowRateGramsPerSecond,
                timeMillis = raw.timeMillis,
                batteryPercent = raw.batteryPercent
            )
        }.stateIn(scope, SharingStarted.WhileSubscribed(5000), ScaleMeasurement(0.0f, 0.0f))

    init {
        // Starta insamling av rådata från klienten
        scope.launch {
            client.measurements.collect { rawData ->
                _rawMeasurement.value = rawData
            }
        }
    }

    override fun startScanDevices(): Flow<List<DiscoveredDevice>> {
        // 1. KONTROLL: Behörigheter
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            // Kastar ett fel som fångas i ViewModel.
            return flow { throw SecurityException("Missing BLUETOOTH_SCAN permission.") }
        }

        // 2. KONTROLL: Bluetooth Adapter Status
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        if (adapter == null || !adapter.isEnabled) {
            // Returnerar ett flow som kastar ett fel om adaptern är null eller avstängd.
            return flow { throw IllegalStateException("Bluetooth is turned off or unavailable.") }
        }

        // 3. Starta skanning via BLE-klienten
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

    override fun connect(address: String) {
        // Nollställ offset vid ny anslutning
        _tareOffset.value = 0.0f
        client.connect(address)
    }

    override fun disconnect() {
        client.disconnect()
        // Nollställ offset vid frånkoppling
        _tareOffset.value = 0.0f
    }

    /** Exponerar den FÄRDIGJUSTERADE mätdataflödet. */
    override fun observeMeasurements(): StateFlow<ScaleMeasurement> = _adjustedMeasurement

    override fun observeConnectionState(): StateFlow<BleConnectionState> = client.connectionState

    /** Skickar tare-kommandot och justerar den lokala offseten. */
    override fun tareScale() {
        _tareOffset.value = _rawMeasurement.value.weightGrams
        client.sendTareCommand()
    }

    /** Skickar tare/start-kommandot och justerar den lokala offseten. */
    override fun tareScaleAndStartTimer() {
        _tareOffset.value = _rawMeasurement.value.weightGrams
        client.sendTareAndStartTimerCommand()
    }

    // --- Pass-through-kommandon ---
    override fun stopTimer() {
        client.sendStopTimerCommand()
    }

    override fun resetTimer() {
        client.sendResetTimerCommand()
    }

    override fun startTimer() {
        client.sendStartTimerCommand()
    }
}