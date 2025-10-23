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

    // --- UPPDATERAD FUNKTION ---
    /**
     * Tolkar bytedatan från vågen.
     * 1. Läser vikt (byte 7-10)
     * 2. Läser flödeshastighet (byte 11-13) om datan finns.
     */
    private fun parseMeasurement(data: ByteArray): ScaleMeasurement? {
        // Vi behöver åtminstone upp till index 9 (BYTE 10) och rätt header
        if (data.size < 10 || data[0] != 0x03.toByte() || data[1] != 0x0B.toByte()) {
            Log.w("BookooBleClient", "Ogiltigt paket: ${data.toHexString()}")
            return null
        }

        // --- Parse Weight (som tidigare) ---
        // Teckenbyte enligt spec: ASCII '+' (0x2B) eller '-' (0x2D)
        val signByte = data[6].toInt() and 0xFF // BYTE 7
        val isNegative = (signByte == 0x2D) // '-'

        // Vikt ligger i gram*100 som 24-bit BIG-endian: High (byte 8), Mid (byte 9), Low (byte 10)
        val wH = data[7].toInt() and 0xFF // BYTE 8
        val wM = data[8].toInt() and 0xFF // BYTE 9
        val wL = data[9].toInt() and 0xFF // BYTE 10
        var rawWeight = (wH shl 16) or (wM shl 8) or wL

        if (isNegative) rawWeight = -rawWeight
        val grams = rawWeight / 100f // Omvandling gram*100 -> gram

        // --- NY KOD: Parse Flow Rate ---
        var flowRate = 0.0f // Default-värde om data saknas

        // Kolla om paketet är tillräckligt långt för flödesdata (vi behöver upp till index 12 / BYTE 13)
        if (data.size >= 13) {
            // Enligt skärmdump: BYTE 11 (data[10]) är +/- symbol för flöde
            val flowSignByte = data[10].toInt() and 0xFF
            val isFlowNegative = (flowSignByte == 0x2D)

            // Enligt skärmdump: BYTE 12 (data[11]) & BYTE 13 (data[12]) är "Flow rate*100"
            val fH = data[11].toInt() and 0xFF // High byte
            val fL = data[12].toInt() and 0xFF // Low byte
            var rawFlow = (fH shl 8) or fL

            if (isFlowNegative) {
                rawFlow = -rawFlow
            }
            // Dela med 100.0f för att få g/s
            flowRate = rawFlow / 100.0f
        }

        // Returnera den nya uppdaterade modellen
        return ScaleMeasurement(grams, flowRate)
    }
    // --- SLUT PÅ UPPDATERAD FUNKTION ---

    private fun ByteArray.toHexString(): String = joinToString(separator = " ") { "%02x".format(it) }

    companion object {
        val BOOKOO_SERVICE_UUID: UUID = UUID.fromString("00000FFE-0000-1000-8000-00805f9B34FB")
        val WEIGHT_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000FF11-0000-1000-8000-00805f9b34fb")
        val COMMAND_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000FF12-0000-1000-8000-00805f9b34fb")
    }
}