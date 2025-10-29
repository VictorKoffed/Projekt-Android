// app/src/main/java/com/victorkoffed/projektandroid/data/ble/BookooBleClient.kt
package com.victorkoffed.projektandroid.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.victorkoffed.projektandroid.domain.model.BleConnectionState
import com.victorkoffed.projektandroid.domain.model.ScaleMeasurement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import java.util.UUID

private const val GATT_SUCCESS_COMPAT = 0
private const val GATT_UNKNOWN_ERROR_COMPAT = -1

@SuppressLint("MissingPermission")
class BookooBleClient(private val context: Context) {

    val connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
    val measurements = MutableSharedFlow<ScaleMeasurement>()
    private val btManager by lazy { context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager }
    private val btAdapter by lazy { btManager.adapter }
    private val scanner by lazy { btAdapter.bluetoothLeScanner }
    private var gatt: BluetoothGatt? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val deviceAddress = gatt.device.address
            if (status == BluetoothGatt.GATT_SUCCESS) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Log.i("BookooBleClient", "Successfully connected to $deviceAddress")
                        connectionState.value = BleConnectionState.Connecting
                        mainHandler.post { gatt.discoverServices() }
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.i("BookooBleClient", "Successfully disconnected from $deviceAddress")
                        handleDisconnect(gatt)
                    }
                }
            } else {
                Log.e("BookooBleClient", "GATT Error on connection state change for $deviceAddress. Status: $status, New state: $newState")
                connectionState.value = BleConnectionState.Error("GATT Error ($status)")
                handleDisconnect(gatt)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(BOOKOO_SERVICE_UUID)
                val weightChar = service?.getCharacteristic(WEIGHT_CHARACTERISTIC_UUID)
                if (weightChar != null) {
                    enableNotifications(gatt, weightChar)
                    // State change moved to onDescriptorWrite for confirmation
                } else {
                    Log.e("BookooBleClient", "Weight characteristic not found for ${gatt.device.address}")
                    connectionState.value = BleConnectionState.Error("Scale characteristic not found")
                    handleDisconnect(gatt)
                }
            } else {
                Log.e("BookooBleClient", "Service discovery failed for ${gatt.device.address} with status: $status")
                connectionState.value = BleConnectionState.Error("Could not find services ($status)")
                handleDisconnect(gatt)
            }
        }

        @Deprecated("Used internally for older Android versions", ReplaceWith("onCharacteristicChanged(gatt, characteristic, characteristic.value)"))
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            handleCharacteristicChanged(characteristic)
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            handleCharacteristicChanged(characteristic, value)
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("BookooBleClient", "Command sent successfully via onCharacteristicWrite! Characteristic: ${characteristic.uuid}")
            } else {
                Log.e("BookooBleClient", "Failed to send command via onCharacteristicWrite, status: $status, Characteristic: ${characteristic.uuid}")
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (descriptor.uuid == CCCD_UUID) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.i("BookooBleClient", "CCCD descriptor write successful for ${descriptor.characteristic.uuid}")
                    // Confirm connection state after notification setup
                    if (connectionState.value is BleConnectionState.Connecting) {
                        connectionState.value = BleConnectionState.Connected(
                            deviceName = gatt.device.name ?: gatt.device.address,
                            deviceAddress = gatt.device.address
                        )
                        Log.i("BookooBleClient", "State set to Connected after CCCD write confirmation.")
                    }
                } else {
                    Log.e("BookooBleClient", "Failed to write CCCD descriptor for ${descriptor.characteristic.uuid}. Status: $status")
                    if (connectionState.value !is BleConnectionState.Error) {
                        connectionState.value = BleConnectionState.Error("Could not enable notifications (CCCD write fail: $status)")
                    }
                    handleDisconnect(gatt)
                }
            } else {
                Log.w("BookooBleClient", "onDescriptorWrite called for unexpected descriptor: ${descriptor.uuid}")
            }
        }
    }

    fun startScan(): Flow<ScanResult> = callbackFlow {
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) { trySend(result).isSuccess }
            override fun onScanFailed(errorCode: Int) { Log.e("BookooBleClient", "BLE scan failed: $errorCode"); close(IllegalStateException("BLE scan failed: $errorCode")) }
        }
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        if (btAdapter?.isEnabled == true) { scanner.startScan(null, settings, callback) } else { close(IllegalStateException("Bluetooth off.")) }
        awaitClose { if (btAdapter?.isEnabled == true) try { scanner?.stopScan(callback) } catch (e: Exception) { Log.w("BookooBleClient", "Error stopping scan: ${e.message}") } }
    }

    fun connect(address: String) {
        val currentState = connectionState.value
        if (currentState is BleConnectionState.Connected && currentState.deviceAddress == address || currentState is BleConnectionState.Connecting) return
        handleDisconnect(gatt) // Ensure clean state before connecting
        mainHandler.post { if (connectionState.value !is BleConnectionState.Connecting) connectionState.value = BleConnectionState.Connecting }
        scope.launch {
            try {
                val device = btAdapter.getRemoteDevice(address); delay(500L) // Allow time for potential cleanup
                val newGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
                mainHandler.post {
                    if (connectionState.value is BleConnectionState.Connecting) {
                        gatt = newGatt
                        if (newGatt == null) {
                            Log.e("BookooBleClient", "connectGatt returned null.")
                            connectionState.value = BleConnectionState.Error("Connect failed (gatt null)")
                        } else {
                            Log.d("BookooBleClient", "connectGatt successful, waiting for callbacks...")
                        }
                    } else {
                        Log.w("BookooBleClient", "State changed during connect, closing new gatt.")
                        handleDisconnect(newGatt) // Cleanup unused gatt instance
                    }
                }
            } catch (e: SecurityException) {
                Log.e("BookooBleClient", "Permission error during connect", e)
                mainHandler.post { connectionState.value = BleConnectionState.Error("Permission missing for connect") }
                handleDisconnect(gatt) // Cleanup potential existing gatt
            } catch (e: IllegalArgumentException) {
                Log.e("BookooBleClient", "Invalid address: $address", e)
                mainHandler.post { connectionState.value = BleConnectionState.Error("Invalid address") }
                handleDisconnect(gatt)
            }
            catch (e: Exception) {
                Log.e("BookooBleClient", "Connect exception", e)
                mainHandler.post { connectionState.value = BleConnectionState.Error("Connection error: ${e.message}") }; handleDisconnect(gatt)
            }
        }
    }

    fun disconnect() {
        val currentGatt = gatt
        if (currentGatt == null) { if (connectionState.value !is BleConnectionState.Disconnected) mainHandler.post { connectionState.value = BleConnectionState.Disconnected }; return }
        handleDisconnect(currentGatt)
    }

    /** Skickar Tare-kommandot (0x01). */
    fun sendTareCommand() {
        // Checksum = 03 ^ 0A ^ 01 ^ 00 ^ 00 = 08
        sendCommand(byteArrayOf(0x03, 0x0A, 0x01, 0x00, 0x00, 0x08), "Tare")
    }

    /** Skickar Tare and Start Timer-kommandot (0x07). */
    fun sendTareAndStartTimerCommand() {
        // Checksum = 03 ^ 0A ^ 07 ^ 00 ^ 00 = 00
        sendCommand(byteArrayOf(0x03, 0x0A, 0x07, 0x00, 0x00, 0x00), "Tare and Start Timer")
    }

    /** Skickar Stop Timer-kommandot (0x05). */
    fun sendStopTimerCommand() {
        // Checksum = 03 ^ 0A ^ 05 ^ 00 ^ 00 = 0D
        sendCommand(byteArrayOf(0x03, 0x0A, 0x05, 0x00, 0x00, 0x0D), "Stop Timer")
    }

    /** Skickar Reset Timer-kommandot (0x06). */
    fun sendResetTimerCommand() {
        // Checksum = 03 ^ 0A ^ 06 ^ 00 ^ 00 = 0C
        sendCommand(byteArrayOf(0x03, 0x0A, 0x06, 0x00, 0x00, 0x0C), "Reset Timer")
    }

    /** Skickar Start Timer-kommandot (0x04). */
    fun sendStartTimerCommand() {
        // Checksum = 03 ^ 0A ^ 04 ^ 00 ^ 00 = 0A
        sendCommand(byteArrayOf(0x03, 0x0A, 0x04, 0x00, 0x00, 0x0A), "Start Timer")
    }

    /** Gemensam funktion för att skicka kommandon till COMMAND_CHARACTERISTIC_UUID. */
    private fun sendCommand(commandBytes: ByteArray, commandName: String) {
        val currentGatt = gatt ?: run { Log.e("BookooBleClient", "Cannot send $commandName: gatt null."); return }
        val commandChar = currentGatt.getService(BOOKOO_SERVICE_UUID)?.getCharacteristic(COMMAND_CHARACTERISTIC_UUID)
        if (commandChar == null) { Log.e("BookooBleClient", "Cannot send $commandName: characteristic null."); return }
        if (commandChar.properties and BluetoothGattCharacteristic.PROPERTY_WRITE == 0) { Log.e("BookooBleClient", "$commandName characteristic not writable."); return }

        mainHandler.post {
            if (gatt != currentGatt || gatt == null) { Log.e("BookooBleClient", "Cannot send $commandName: gatt changed or became null before write."); return@post }
            val writeResult = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // Log.d("BookooBleClient", "Using writeCharacteristic (API 33+) for $commandName")
                    currentGatt.writeCharacteristic(commandChar, commandBytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                } else {
                    // Log.d("BookooBleClient", "Using legacy writeCharacteristic for $commandName")
                    @Suppress("DEPRECATION") commandChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    @Suppress("DEPRECATION") commandChar.value = commandBytes
                    @Suppress("DEPRECATION") if (currentGatt.writeCharacteristic(commandChar)) GATT_SUCCESS_COMPAT else GATT_UNKNOWN_ERROR_COMPAT
                }
            } catch (e: SecurityException) { Log.e("BookooBleClient", "Permission error writing $commandName", e); connectionState.value = BleConnectionState.Error("Permission missing for write"); GATT_UNKNOWN_ERROR_COMPAT }
            catch (e: Exception) { Log.e("BookooBleClient", "Unexpected error writing $commandName", e); connectionState.value = BleConnectionState.Error("Error sending command"); GATT_UNKNOWN_ERROR_COMPAT }

            if (writeResult == GATT_SUCCESS_COMPAT) {
                Log.d("BookooBleClient", "$commandName command write initiated successfully.")
            } else {
                Log.e("BookooBleClient", "Failed to initiate $commandName command write. Error: $writeResult (Characteristic: ${commandChar.uuid})")
                if (connectionState.value !is BleConnectionState.Error) { connectionState.value = BleConnectionState.Error("Failed to send $commandName ($writeResult)") }
            }
        }
    }


    private fun handleCharacteristicChanged(characteristic: BluetoothGattCharacteristic, value: ByteArray? = null) {
        val data: ByteArray? = value ?: characteristic.value
        if (characteristic.uuid == WEIGHT_CHARACTERISTIC_UUID && data != null) { parseMeasurement(data)?.let { scope.launch { measurements.emit(it) } } }
        else if (data == null) { Log.w("BookooBleClient", "Characteristic ${characteristic.uuid} changed but data was null.") }
    }

    private fun handleDisconnect(gattInstance: BluetoothGatt?) {
        if (gattInstance == null) return
        val address = gattInstance.device.address
        Log.d("BookooBleClient", "Handling disconnect/cleanup for $address")
        try { gattInstance.disconnect(); gattInstance.close() }
        catch (e: Exception) { Log.e("BookooBleClient", "Exception during GATT cleanup for $address", e) }
        finally { mainHandler.post { if (this.gatt == gattInstance) this.gatt = null; val cs = connectionState.value; if (cs !is BleConnectionState.Disconnected && cs !is BleConnectionState.Error) connectionState.value = BleConnectionState.Disconnected } }
    }

    private fun enableNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        val descriptor = characteristic.getDescriptor(CCCD_UUID) ?: run { Log.e("BookooBleClient", "CCCD descriptor missing"); mainHandler.post { connectionState.value = BleConnectionState.Error("CCCD missing") }; handleDisconnect(gatt); return }
        mainHandler.post {
            if (this.gatt != gatt || gatt == null) return@post
            try {
                if (!gatt.setCharacteristicNotification(characteristic, true)) { Log.e("BookooBleClient", "setCharacteristicNotification failed"); if (connectionState.value !is BleConnectionState.Error) connectionState.value = BleConnectionState.Error("Notify fail (local)"); handleDisconnect(gatt); return@post }
                val writePayload = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                val writeResult = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) gatt.writeDescriptor(descriptor, writePayload) else { @Suppress("DEPRECATION") descriptor.value = writePayload; @Suppress("DEPRECATION") if (gatt.writeDescriptor(descriptor)) GATT_SUCCESS_COMPAT else GATT_UNKNOWN_ERROR_COMPAT }
                if (writeResult != GATT_SUCCESS_COMPAT) { Log.e("BookooBleClient", "Failed CCCD write: $writeResult"); if (connectionState.value !is BleConnectionState.Error) connectionState.value = BleConnectionState.Error("Notify fail (write CCCD $writeResult)"); handleDisconnect(gatt) }
                else Log.i("BookooBleClient", "CCCD write initiated for ${characteristic.uuid}")
            } catch (e: Exception) { Log.e("BookooBleClient", "Exception enabling notifications", e); mainHandler.post { connectionState.value = BleConnectionState.Error("Notify exception: ${e.message}") }; handleDisconnect(gatt) }
        }
    }


    /** Parsar vågdata (inkl. tid). */
    private fun parseMeasurement(data: ByteArray): ScaleMeasurement? {
        if (data.size < 10 || data.getOrNull(0) != 0x03.toByte() || data.getOrNull(1) != 0x0B.toByte()) { /*Log.w("BookooBleClient", "Invalid pkg: ${data.toHexString()}");*/ return null } // Reduce log spam
        try {
            val msH=data[2].toInt() and 0xFF; val msM=data[3].toInt() and 0xFF; val msL=data[4].toInt() and 0xFF; val scaleTimeMillis=((msH shl 16) or (msM shl 8) or msL).toLong()
            val sign=data[6].toInt() and 0xFF; val neg=sign==0x2D; val wH=data[7].toInt() and 0xFF; val wM=data[8].toInt() and 0xFF; val wL=data[9].toInt() and 0xFF; var rawW=(wH shl 16) or (wM shl 8) or wL; if(neg && rawW!=0) rawW=-rawW; val grams=rawW.toFloat()/100.0f
            var flow=0.0f; if(data.size>=13){ val fSign=data[10].toInt() and 0xFF; val fNeg=fSign==0x2D; val fH=data[11].toInt() and 0xFF; val fL=data[12].toInt() and 0xFF; var rawF=(fH shl 8) or fL; if(fNeg && rawF!=0) rawF=-rawF; flow=rawF.toFloat()/100.0f }
            return ScaleMeasurement(grams, flow, scaleTimeMillis)
        } catch (e: Exception) { Log.e("BookooBleClient", "Parse error: ${data.toHexString()}", e); return null }
    }

    private fun ByteArray.toHexString(): String = joinToString(separator = " ") { "%02x".format(it) }

    companion object {
        val BOOKOO_SERVICE_UUID: UUID = UUID.fromString("00000FFE-0000-1000-8000-00805f9B34FB")
        val WEIGHT_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000FF11-0000-1000-8000-00805f9b34fb")
        val COMMAND_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000FF12-0000-1000-8000-00805f9b34fb")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}