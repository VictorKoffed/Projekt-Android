package com.victorkoffed.projektandroid.data.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.victorkoffed.projektandroid.domain.model.BleConnectionState
import com.victorkoffed.projektandroid.domain.model.ScaleMeasurement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import java.util.*

@SuppressLint("MissingPermission")
class BookooBleClient(private val context: Context) {

    // --- Flows for exposing data ---
    val connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
    val measurements = MutableSharedFlow<ScaleMeasurement>()

    // --- Bluetooth Properties ---
    private val btManager by lazy { context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager }
    private val btAdapter by lazy { btManager.adapter }
    private val scanner by lazy { btAdapter.bluetoothLeScanner }
    private var gatt: BluetoothGatt? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    // --- GATT Callback ---
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        connectionState.value = BleConnectionState.Connecting
                        mainHandler.post { gatt.discoverServices() }
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> disconnect()
                }
            } else {
                Log.e("BookooBleClient", "GATT Error on connection. Status: $status")
                connectionState.value = BleConnectionState.Error("GATT-fel: $status")
                gatt.close()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(BOOKOO_SERVICE_UUID)
                val weightChar = service?.getCharacteristic(WEIGHT_CHARACTERISTIC_UUID)
                if (weightChar != null) {
                    enableNotifications(gatt, weightChar)
                    connectionState.value = BleConnectionState.Connected(gatt.device.name ?: "Okänd enhet")
                } else {
                    connectionState.value = BleConnectionState.Error("Våg-karakteristik hittades ej")
                    gatt.close()
                }
            } else {
                connectionState.value = BleConnectionState.Error("Hittade inte services")
                gatt.close()
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == WEIGHT_CHARACTERISTIC_UUID) {
                parseMeasurement(characteristic.value)?.let {
                    scope.launch { measurements.emit(it) }
                }
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("BookooBleClient", "Kommando skickat!")
            } else {
                Log.e("BookooBleClient", "Misslyckades att skicka kommando, status: $status")
            }
        }
    }

    // --- Public API ---
    fun startScan(): Flow<ScanResult> = callbackFlow {
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) { trySend(result) }
            override fun onScanFailed(errorCode: Int) { close(IllegalStateException("BLE scan failed with code: $errorCode")) }
        }
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner.startScan(null, settings, callback)
        awaitClose { scanner.stopScan(callback) }
    }

    fun connect(address: String) {
        if (connectionState.value != BleConnectionState.Disconnected) return
        connectionState.value = BleConnectionState.Connecting
        val device = btAdapter.getRemoteDevice(address)
        gatt = device.connectGatt(context, false, gattCallback)
    }

    fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        connectionState.value = BleConnectionState.Disconnected
    }

    fun sendTareCommand() {
        val commandChar = gatt?.getService(BOOKOO_SERVICE_UUID)?.getCharacteristic(COMMAND_CHARACTERISTIC_UUID)
        if (commandChar == null) {
            Log.e("BookooBleClient", "Command characteristic not found.")
            return
        }
        val tareCommand = byteArrayOf(0x03, 0x0A, 0x01, 0x00, 0x00, 0x08)
        commandChar.value = tareCommand
        commandChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        val success = gatt?.writeCharacteristic(commandChar)
        Log.d("BookooBleClient", "Attempting to send tare command... Success: $success")
    }

    // --- Private Helper Functions ---
    private fun enableNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        val cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        val descriptor = characteristic.getDescriptor(cccdUuid)
        gatt.setCharacteristicNotification(characteristic, true)
        descriptor?.let {
            it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(it)
        }
    }

    /**
     * Tolkar bytedatan från vågen. Denna metod är den slutgiltiga versionen
     * baserad på empiriska tester som visar ett 256x fel med 3-bytes läsning.
     * 1. Läser 2 bytes (16 bitar) för att få rätt skala.
     * 2. Använder en fast divisor på 100.0f som specificerat.
     */
    private fun parseMeasurement(data: ByteArray): ScaleMeasurement? {
        // Vi behöver åtminstone upp till index 9 och rätt header
        if (data.size < 10 || data[0] != 0x03.toByte() || data[1] != 0x0B.toByte()) {
            Log.w("BookooBleClient", "Ogiltigt paket: ${data.toHexString()}")
            return null
        }

        // Teckenbyte enligt spec: ASCII '+' (0x2B) eller '-' (0x2D)
        val signByte = data[6].toInt() and 0xFF
        val isNegative = (signByte == 0x2D) // '-'

        // Vikt ligger i gram*100 som 24-bit BIG-endian: High (byte7), Mid (byte8), Low (byte9)
        val wH = data[7].toInt() and 0xFF
        val wM = data[8].toInt() and 0xFF
        val wL = data[9].toInt() and 0xFF
        var raw = (wH shl 16) or (wM shl 8) or wL

        // Om negativt: neguera värdet
        if (isNegative) raw = -raw

        // Omvandling gram*100 -> gram
        val grams = raw / 100f

        return ScaleMeasurement(grams)
    }

    private fun ByteArray.toHexString(): String = joinToString(separator = " ") { "%02x".format(it) }

    companion object {
        val BOOKOO_SERVICE_UUID: UUID = UUID.fromString("00000FFE-0000-1000-8000-00805f9B34FB")
        val WEIGHT_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000FF11-0000-1000-8000-00805f9b34fb")
        val COMMAND_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000FF12-0000-1000-8000-00805f9b34fb")
    }
}

