/**
 * EXTERNAL PROTOCOL: Hardware command orchestration (e.g., taring, timer controls)
 * strictly follows the Bookoo BLE Protocol specifications.
 * Source: https://github.com/BooKooCode/OpenSource/blob/main/bookoo_mini_scale/protocols.md
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
 * Concrete implementation of [ScaleRepository] for the Bookoo hardware series.
 * Acts as the bridge between the raw BLE client and the application domain.
 * Manages logical state constraints like local taring offsets to provide a seamless
 * zeroed measurement stream to the UI, compensating for BLE latency when physical tare commands are issued.
 */
@Singleton
class BookooScaleRepositoryImpl @Inject constructor(
    @param:ApplicationContext @field:ApplicationContext private val context: Context
) : ScaleRepository {

    private val client: BookooBleClient by lazy { BookooBleClient(context) }
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private val _rawMeasurement = MutableStateFlow(ScaleMeasurement(0.0f, 0.0f))
    private val _tareOffset = MutableStateFlow(0.0f)

    /**
     * Provides an immediately zeroed measurement stream.
     * By calculating a local offset against the raw incoming hardware data, we ensure
     * the UI reflects a "0.0g" state instantly upon a tare action, without waiting
     * for the hardware round-trip response.
     */
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
        scope.launch {
            client.measurements.collect { rawData ->
                _rawMeasurement.value = rawData
            }
        }
    }

    override fun startScanDevices(): Flow<List<DiscoveredDevice>> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            return flow { throw SecurityException("Missing BLUETOOTH_SCAN permission.") }
        }

        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        if (adapter == null || !adapter.isEnabled) {
            return flow { throw IllegalStateException("Bluetooth is turned off or unavailable.") }
        }

        return client.startScan()
            .map { result ->
                DiscoveredDevice(
                    name = result.device.name ?: result.device.address,
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
        _tareOffset.value = 0.0f
        client.connect(address)
    }

    override fun disconnect() {
        client.disconnect()
        _tareOffset.value = 0.0f
    }

    override fun observeMeasurements(): StateFlow<ScaleMeasurement> = _adjustedMeasurement

    override fun observeConnectionState(): StateFlow<BleConnectionState> = client.connectionState

    /**
     * Initiates a physical hardware tare while simultaneously establishing a local software offset.
     * This provides immediate UI feedback while the hardware processes the command.
     */
    override fun tareScale() {
        _tareOffset.value = _rawMeasurement.value.weightGrams
        client.sendTareCommand()
    }

    /**
     * Initiates a simultaneous physical hardware tare and timer start, while establishing a local offset.
     */
    override fun tareScaleAndStartTimer() {
        _tareOffset.value = _rawMeasurement.value.weightGrams
        client.sendTareAndStartTimerCommand()
    }

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