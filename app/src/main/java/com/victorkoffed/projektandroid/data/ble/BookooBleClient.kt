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
private const val TAG = "BookooBleClient"

@SuppressLint("MissingPermission")
class BookooBleClient(private val context: Context) {

    val connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
    val measurements = MutableSharedFlow<ScaleMeasurement>()
    private val btManager by lazy { context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager }
    private val btAdapter by lazy { btManager.adapter }
    private val scanner by lazy { btAdapter.bluetoothLeScanner }
    private var gatt: BluetoothGatt? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    // Använder Dispatchers.IO för att undvika blockering av Main Thread
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> handleConnectionSuccess(gatt)
                    BluetoothProfile.STATE_DISCONNECTED -> handleConnectionDisconnected(gatt)
                }
            } else {
                handleGattError(gatt, status, "onConnectionStateChange", "GATT Error ($status)")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                handleServiceDiscoverySuccess(gatt)
            } else {
                handleGattError(gatt, status, "onServicesDiscovered", "Could not find services ($status)")
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
                Log.d(TAG, "Command sent successfully: ${characteristic.uuid}")
            } else {
                Log.e(TAG, "Failed to send command, status: $status, Char: ${characteristic.uuid}")
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (descriptor.uuid == CCCD_UUID) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    handleCccdWriteSuccess(gatt)
                } else {
                    handleGattError(gatt, status, "onDescriptorWrite", "Could not enable notifications (CCCD write fail: $status)")
                }
            }
        }
    }

    /** Startar BLE-skanning och skickar ScanResult via Flow. */
    fun startScan(): Flow<ScanResult> = callbackFlow {
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) { trySend(result).isSuccess }
            override fun onScanFailed(errorCode: Int) { Log.e(TAG, "BLE scan failed: $errorCode"); close(IllegalStateException("Bluetooth off.")) }
        }
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        if (btAdapter?.isEnabled == true) { scanner.startScan(null, settings, callback) } else { close(IllegalStateException("Bluetooth off.")) }
        awaitClose { if (btAdapter?.isEnabled == true) try { scanner.stopScan(callback) } catch (e: Exception) { Log.w(TAG, "Error stopping scan: ${e.message}") } }
    }

    /** Initierar anslutning till en enhet med den givna BLE-adressen. */
    fun connect(address: String) {
        val currentState = connectionState.value
        if ((currentState is BleConnectionState.Connected && currentState.deviceAddress == address) || currentState is BleConnectionState.Connecting) return
        handleGattCleanup(gatt) // Säkerställ en ren stat före anslutning
        mainHandler.post { if (connectionState.value !is BleConnectionState.Connecting) connectionState.value = BleConnectionState.Connecting }

        scope.launch {
            try {
                val device = btAdapter.getRemoteDevice(address); delay(500L)
                val newGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)

                mainHandler.post {
                    if (connectionState.value is BleConnectionState.Connecting) {
                        gatt = newGatt
                        if (newGatt == null) {
                            Log.e(TAG, "connectGatt returned null.")
                            connectionState.value = BleConnectionState.Error("Connect failed (gatt null)")
                        } else {
                            Log.d(TAG, "connectGatt successful, waiting for callbacks...")
                        }
                    } else {
                        Log.w(TAG, "State changed during connect, closing new gatt.")
                        handleGattCleanup(newGatt) // Städa upp oanvänd gatt-instans
                    }
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Permission error during connect", e)
                mainHandler.post { connectionState.value = BleConnectionState.Error("Permission missing for connect") }
                handleGattCleanup(gatt)
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Invalid address: $address", e)
                mainHandler.post { connectionState.value = BleConnectionState.Error("Invalid address") }
                handleGattCleanup(gatt)
            }
            catch (e: Exception) {
                Log.e(TAG, "Connect exception", e)
                mainHandler.post { connectionState.value = BleConnectionState.Error("Connection error: ${e.message}") }; handleGattCleanup(gatt)
            }
        }
    }

    /** Stänger den nuvarande BLE-anslutningen. */
    fun disconnect() {
        handleGattCleanup(gatt)
    }

    // --- Hjälpfunktioner för GATT Callback-logik (Utbruten logik) ---

    private fun handleConnectionSuccess(gatt: BluetoothGatt) {
        Log.i(TAG, "Successfully connected to ${gatt.device.address}")
        connectionState.value = BleConnectionState.Connecting
        // Service discovery MÅSTE starta på main thread
        mainHandler.post { gatt.discoverServices() }
    }

    private fun handleConnectionDisconnected(gatt: BluetoothGatt) {
        Log.i(TAG, "Successfully disconnected from ${gatt.device.address}")
        handleGattCleanup(gatt)
    }

    private fun handleServiceDiscoverySuccess(gatt: BluetoothGatt) {
        val service = gatt.getService(BOOKOO_SERVICE_UUID)
        val weightChar = service?.getCharacteristic(WEIGHT_CHARACTERISTIC_UUID)
        if (weightChar != null) {
            enableNotifications(gatt, weightChar)
        } else {
            Log.e(TAG, "Weight characteristic not found for ${gatt.device.address}")
            connectionState.value = BleConnectionState.Error("Scale characteristic not found")
            handleGattCleanup(gatt)
        }
    }

    private fun handleCccdWriteSuccess(gatt: BluetoothGatt) {
        Log.i(TAG, "CCCD descriptor write successful for ${gatt.device.address}")
        // Bekräfta anslutningsstatus efter att notifikationer är aktiverade
        if (connectionState.value is BleConnectionState.Connecting) {
            connectionState.value = BleConnectionState.Connected(
                deviceName = gatt.device.name ?: gatt.device.address,
                deviceAddress = gatt.device.address,
                batteryPercent = null // Batteriet uppdateras i handleCharacteristicChanged
            )
            Log.i(TAG, "State set to Connected after CCCD write confirmation.")
        }
    }

    private fun handleGattError(gatt: BluetoothGatt, status: Int, source: String, userMessage: String) {
        Log.e(TAG, "GATT Error ($status) in $source for ${gatt.device.address}. Message: $userMessage")
        // Uppdatera endast om vi inte redan är i ett fel-tillstånd
        if (connectionState.value !is BleConnectionState.Error) {
            connectionState.value = BleConnectionState.Error(userMessage)
        }
        handleGattCleanup(gatt)
    }

    /** Städar upp GATT-resurser och sätter disonnected state. */
    private fun handleGattCleanup(gattInstance: BluetoothGatt?) {
        if (gattInstance == null) return
        val address = gattInstance.device.address
        Log.d(TAG, "Handling disconnect/cleanup for $address")
        try { gattInstance.disconnect(); gattInstance.close() }
        catch (e: Exception) { Log.e(TAG, "Exception during GATT cleanup for $address", e) }
        finally {
            mainHandler.post {
                if (this.gatt == gattInstance) this.gatt = null
                val cs = connectionState.value
                // Endast sätt Disconnected om vi inte är i ett ohanterat Error state
                if (cs !is BleConnectionState.Disconnected && cs !is BleConnectionState.Error) {
                    connectionState.value = BleConnectionState.Disconnected
                }
            }
        }
    }

    private fun enableNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        val descriptor = characteristic.getDescriptor(CCCD_UUID) ?: run {
            Log.e(TAG, "CCCD descriptor missing for ${characteristic.uuid}")
            mainHandler.post { connectionState.value = BleConnectionState.Error("CCCD missing") }
            handleGattCleanup(gatt)
            return
        }

        mainHandler.post {
            // Kontrollera att gatt inte har nollställts under tiden
            val currentGatt = this.gatt
            if (currentGatt != gatt) return@post
            try {
                if (!currentGatt.setCharacteristicNotification(characteristic, true)) {
                    Log.e(TAG, "setCharacteristicNotification failed")
                    if (connectionState.value !is BleConnectionState.Error) {
                        connectionState.value = BleConnectionState.Error("Notify fail (local)")
                    }
                    handleGattCleanup(currentGatt)
                    return@post
                }

                val writePayload = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                val writeResult = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // Modern, icke-deprekerad metod (API 33+)
                    currentGatt.writeDescriptor(descriptor, writePayload)
                    GATT_SUCCESS_COMPAT
                } else {
                    // Äldre metod – lägg suppress på ett block, inte på LHS av en tilldelning/uttryck
                    val success = @Suppress("DEPRECATION") run {
                        descriptor.value = writePayload
                        currentGatt.writeDescriptor(descriptor)
                    }
                    if (success) GATT_SUCCESS_COMPAT else GATT_UNKNOWN_ERROR_COMPAT
                }

                if (writeResult != GATT_SUCCESS_COMPAT) {
                    Log.e(TAG, "Failed CCCD write initiation: $writeResult")
                    if (connectionState.value !is BleConnectionState.Error) {
                        connectionState.value = BleConnectionState.Error("Notify fail (write CCCD $writeResult)")
                    }
                    handleGattCleanup(currentGatt)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception enabling notifications", e)
                mainHandler.post { connectionState.value = BleConnectionState.Error("Notify exception: ${e.message}") }
                handleGattCleanup(currentGatt)
            }
        }
    }

    /** Hanterar inkommande data från vågen via notifikationer. */
    private fun handleCharacteristicChanged(characteristic: BluetoothGattCharacteristic, value: ByteArray? = null) {
        // Fix: modernt sätt att hämta värde.
        val data: ByteArray? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            value
        } else {
            @Suppress("DEPRECATION")
            value ?: characteristic.value
        }

        if (characteristic.uuid == WEIGHT_CHARACTERISTIC_UUID && data != null) {
            BookooDataParser.parseMeasurement(data)?.let { measurement ->
                // Emit measurement (för LiveBrewScreen)
                scope.launch { measurements.emit(measurement) }

                // Uppdatera connectionState med batterinivå
                val currentState = connectionState.value
                if (currentState is BleConnectionState.Connected && measurement.batteryPercent != null) {
                    if (currentState.batteryPercent != measurement.batteryPercent) {
                        connectionState.value = currentState.copy(batteryPercent = measurement.batteryPercent)
                    }
                }
            }
        } else if (data == null) {
            Log.w(TAG, "Characteristic ${characteristic.uuid} changed but data was null.")
        }
    }

    // --- Funktioner för att skicka kommandon ---

    /** Skickar Tare-kommandot (0x01). */
    fun sendTareCommand() {
        // Checksum = 03 ^ 0A ^ 01 ^ 00 ^ 00 = 08
        sendCommand(byteArrayOf(0x03, 0x0A, 0x01, 0x00, 0x00, 0x08), "Tare")
    }

    /** Skickar Tare and Start Timer-kommandot (0x07). */
    fun sendTareAndStartTimerCommand() {
        // Checksum = 03 ^ 0A ^ 07 ^ 00 ^ 00 = 00
        // Fix: Lade till en nolla i slutet av datapaketet. De andra kommandona har 6 bytes.
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
    @SuppressLint("MissingPermission")
    private fun sendCommand(commandBytes: ByteArray, commandName: String) {
        val currentGatt = gatt ?: run { Log.e(TAG, "Cannot send $commandName: gatt null."); return }
        val commandChar = currentGatt.getService(BOOKOO_SERVICE_UUID)?.getCharacteristic(COMMAND_CHARACTERISTIC_UUID)
        if (commandChar == null) { Log.e(TAG, "Cannot send $commandName: characteristic null."); return }
        if (commandChar.properties and BluetoothGattCharacteristic.PROPERTY_WRITE == 0) { Log.e(TAG, "$commandName characteristic not writable."); return }

        mainHandler.post {
            val gattInstance = gatt
            if (gattInstance != currentGatt) {
                Log.e(TAG, "Cannot send $commandName: gatt changed or became null before write.")
                return@post
            }

            val writeResult = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // Modern, icke-deprekerad metod (API 33+)
                    gattInstance.writeCharacteristic(commandChar, commandBytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                    GATT_SUCCESS_COMPAT
                } else {
                    // Äldre metod – lägg suppress på ett block
                    val success = @Suppress("DEPRECATION") run {
                        commandChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                        commandChar.value = commandBytes
                        gattInstance.writeCharacteristic(commandChar)
                    }
                    if (success) GATT_SUCCESS_COMPAT else GATT_UNKNOWN_ERROR_COMPAT
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Permission error writing $commandName", e)
                connectionState.value = BleConnectionState.Error("Permission missing for write"); GATT_UNKNOWN_ERROR_COMPAT
            }
            catch (e: Exception) {
                Log.e(TAG, "Unexpected error writing $commandName", e)
                connectionState.value = BleConnectionState.Error("Error sending command"); GATT_UNKNOWN_ERROR_COMPAT
            }

            if (writeResult != GATT_SUCCESS_COMPAT) {
                Log.e(TAG, "Failed to initiate $commandName command write. Error: $writeResult (Char: ${commandChar.uuid})")
                if (connectionState.value !is BleConnectionState.Error) { connectionState.value = BleConnectionState.Error("Failed to send $commandName ($writeResult)") }
            }
        }
    }

    companion object {
        val BOOKOO_SERVICE_UUID: UUID = UUID.fromString("00000FFE-0000-1000-8000-00805f9B34FB")
        val WEIGHT_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000FF11-0000-1000-8000-00805f9b34fb")
        val COMMAND_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000FF12-0000-1000-8000-00805f9b34fb")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
