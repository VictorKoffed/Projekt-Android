package com.victorkoffed.projektandroid.data.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build // <-- NY IMPORT
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
import java.util.*
import android.bluetooth.BluetoothProfile

// --- KONSTANTER FÖR API 31-VÄRDEN ---
private const val GATT_SUCCESS_COMPAT = 0 // Motsvarar BluetoothStatusCodes.SUCCESS och BluetoothGatt.GATT_SUCCESS
private const val GATT_UNKNOWN_ERROR_COMPAT = -1 // Motsvarar BluetoothStatusCodes.ERROR_UNKNOWN

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
            val deviceAddress = gatt.device.address // Hämta adressen för loggning/felsökning
            if (status == BluetoothGatt.GATT_SUCCESS) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Log.i("BookooBleClient", "Successfully connected to $deviceAddress")
                        connectionState.value = BleConnectionState.Connecting // Väntar på service discovery
                        // Kör discoverServices på huvudtråden för att undvika race conditions
                        mainHandler.post {
                            // Dubbelkolla att gatt inte är null innan anrop
                            this@BookooBleClient.gatt?.discoverServices()
                        }
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.i("BookooBleClient", "Successfully disconnected from $deviceAddress")
                        // Anropa din städmetod här
                        handleDisconnect(gatt)
                    }
                }
            } else {
                // Fel uppstod under anslutning/frånkoppling
                Log.e("BookooBleClient", "GATT Error on connection state change for $deviceAddress. Status: $status, New state: $newState")
                // Sätt state till Error FÖRE handleDisconnect, så UI kan reagera
                connectionState.value = BleConnectionState.Error("GATT Error ($status)")
                // Stäng och rensa vid fel
                handleDisconnect(gatt) // Använd samma städmetod
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(BOOKOO_SERVICE_UUID)
                val weightChar = service?.getCharacteristic(WEIGHT_CHARACTERISTIC_UUID)
                if (weightChar != null) {
                    enableNotifications(gatt, weightChar)
                    // Sätt Connected endast om state fortfarande är Connecting (undvik race condition om disconnect hänt under tiden)
                    if (connectionState.value is BleConnectionState.Connecting) {
                        connectionState.value = BleConnectionState.Connected(gatt.device.name ?: gatt.device.address) // Använd adress som fallback
                        Log.i("BookooBleClient", "Services discovered and notifications enabled for ${gatt.device.address}")
                    } else {
                        Log.w("BookooBleClient", "Services discovered for ${gatt.device.address}, but state was not Connecting. Current state: ${connectionState.value}")
                    }
                } else {
                    Log.e("BookooBleClient", "Weight characteristic not found for ${gatt.device.address}")
                    connectionState.value = BleConnectionState.Error("Scale characteristic not found")
                    handleDisconnect(gatt) // Städa upp
                }
            } else {
                Log.e("BookooBleClient", "Service discovery failed for ${gatt.device.address} with status: $status")
                connectionState.value = BleConnectionState.Error("Could not find services ($status)")
                handleDisconnect(gatt) // Städa upp
            }
        }

        @Deprecated("Deprecated in Java") // Lägg till denna för att tysta varningen för hela funktionen
        @Suppress("DEPRECATION") // Tysta varningen specifikt för characteristic.value
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == WEIGHT_CHARACTERISTIC_UUID) {
                // characteristic.value är deprecated, men fungerar. getValue() är alternativet.
                parseMeasurement(characteristic.value)?.let {
                    scope.launch { measurements.emit(it) }
                }
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("BookooBleClient", "Command sent!")
            } else {
                Log.e("BookooBleClient", "Failed to send command, status: $status")
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
        // --- NYTT: Kontrollera att Bluetooth är på ---
        if (btAdapter?.isEnabled == true) {
            scanner.startScan(null, settings, callback)
        } else {
            close(IllegalStateException("Bluetooth is not enabled."))
        }
        // --- SLUT NYTT ---
        awaitClose {
            if (btAdapter?.isEnabled == true) { // Förhindra krasch om BT stängs av under skanning
                scanner?.stopScan(callback) // Lägg till null-check på scanner
            }
        }
    }

    // --- UPPDATERAD connect METOD ---
    fun connect(address: String) {
        // Om vi redan är anslutna till *samma* enhet, gör inget.
        if (connectionState.value is BleConnectionState.Connected && gatt?.device?.address == address) {
            Log.w("BookooBleClient", "Already connected to $address. Ignoring connect request.")
            return
        }
        // Om vi redan försöker ansluta (Connecting), gör inget för att undvika race conditions
        if (connectionState.value is BleConnectionState.Connecting) {
            Log.w("BookooBleClient", "Connection attempt already in progress. Ignoring connect request to $address.")
            return
        }

        Log.i("BookooBleClient", "Attempting to connect to $address...")

        // Städa upp eventuell *gammal* gatt-instans FÖRST
        handleDisconnect(gatt) // Använd hjälpmetoden som redan sätter gatt till null och state till Disconnected

        // Sätt state till Connecting EFTER att den gamla är helt bortkopplad
        connectionState.value = BleConnectionState.Connecting

        // Kör resten i en Coroutine för att kunna använda delay
        scope.launch {
            try {
                val device = btAdapter.getRemoteDevice(address)

                // --- NYTT: Lägg till en kort fördröjning ---
                delay(100L) // Vänta 100 millisekunder
                // --- SLUT NYTT ---

                // Skapa ALLTID en ny gatt-instans här (körs på IO-tråd)
                val newGatt = device.connectGatt(
                    context,
                    false, // autoConnect = false
                    gattCallback,
                    BluetoothDevice.TRANSPORT_LE // Explicit begär LE transport
                )

                // Posta tillbaka till huvudtråden för att uppdatera gatt-variabeln säkert
                mainHandler.post {
                    if (connectionState.value is BleConnectionState.Connecting) { // Dubbelkolla state
                        gatt = newGatt // Spara den nya instansen
                        if (gatt == null) {
                            Log.e("BookooBleClient", "device.connectGatt returned null for $address.")
                            connectionState.value = BleConnectionState.Error("Could not initiate connection")
                        } else {
                            Log.d("BookooBleClient", "connectGatt called successfully for $address. Waiting for callback...")
                        }
                    } else {
                        // Om state ändrats under tiden (t.ex. användaren avbröt), stäng den nya anslutningen direkt
                        Log.w("BookooBleClient", "State changed during connect setup for $address. Closing new GATT instance.")
                        handleDisconnect(newGatt)
                    }
                }

            } catch (e: SecurityException) {
                Log.e("BookooBleClient", "Bluetooth permission missing for connect", e)
                mainHandler.post { connectionState.value = BleConnectionState.Error("Bluetooth permission missing") }
            } catch (e: IllegalArgumentException) {
                Log.e("BookooBleClient", "Invalid Bluetooth address: $address", e)
                mainHandler.post { connectionState.value = BleConnectionState.Error("Invalid address") }
            } catch (e: Exception) {
                Log.e("BookooBleClient", "Unexpected error during connect to $address", e)
                mainHandler.post { connectionState.value = BleConnectionState.Error("Unexpected connection error") }
                handleDisconnect(gatt) // Försök städa upp
            }
        }
    }
    // --- SLUT UPPDATERAD connect ---

    fun disconnect() {
        val currentGatt = gatt
        if (currentGatt == null) {
            Log.w("BookooBleClient", "Disconnect called but gatt is already null.")
            if (connectionState.value !is BleConnectionState.Disconnected) {
                connectionState.value = BleConnectionState.Disconnected
            }
            return
        }
        Log.i("BookooBleClient", "Disconnecting from ${currentGatt.device.address}...")
        handleDisconnect(currentGatt)
    }

    @Suppress("DEPRECATION") // Tysta varningen för den äldre writeCharacteristic/value-användningen
    fun sendTareCommand() {
        val commandChar = gatt?.getService(BOOKOO_SERVICE_UUID)?.getCharacteristic(COMMAND_CHARACTERISTIC_UUID)
        if (gatt == null || commandChar == null) {
            Log.e("BookooBleClient", "Cannot send tare command: gatt or command characteristic not found.")
            return
        }
        val tareCommand = byteArrayOf(0x03, 0x0A, 0x01, 0x00, 0x00, 0x08)

        // Förbättrad skrivning
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt?.writeCharacteristic(commandChar, tareCommand, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) ?: GATT_UNKNOWN_ERROR_COMPAT // Använd kompatibel konstant
        } else {
            // För äldre versioner
            commandChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            commandChar.value = tareCommand // .value är deprecated men fungerar
            if (gatt?.writeCharacteristic(commandChar) == true) { // writeCharacteristic är deprecated men fungerar
                GATT_SUCCESS_COMPAT // Använd kompatibel konstant
            } else {
                GATT_UNKNOWN_ERROR_COMPAT // Använd kompatibel konstant
            }
        }

        if (result == GATT_SUCCESS_COMPAT) {
            Log.d("BookooBleClient", "Tare command write operation initiated successfully.")
        } else {
            Log.e("BookooBleClient", "Failed to initiate tare command write operation. Error code: $result")
        }
    }


    // --- Private Helper Functions ---
    private fun handleDisconnect(gattInstance: BluetoothGatt?) {
        val address = gattInstance?.device?.address ?: "unknown device"
        Log.d("BookooBleClient", "Handling disconnect/cleanup for $address")
        try {
            gattInstance?.disconnect()
            gattInstance?.close()
        } catch (e: SecurityException) {
            Log.e("BookooBleClient", "Bluetooth permission missing during cleanup for $address", e)
        } catch (e: Exception) {
            Log.e("BookooBleClient", "Exception during GATT cleanup for $address", e)
        } finally {
            if (this.gatt == gattInstance) {
                this.gatt = null
                Log.d("BookooBleClient", "Current gatt instance nulled for $address")
            } else {
                Log.w("BookooBleClient", "Skipped nulling gatt, instance mismatch during cleanup for $address")
            }
            if (connectionState.value !is BleConnectionState.Disconnected) {
                // Sätt Disconnected via mainHandler för trådsäkerhet
                mainHandler.post {
                    if (connectionState.value !is BleConnectionState.Disconnected) {
                        connectionState.value = BleConnectionState.Disconnected
                        Log.i("BookooBleClient", "Connection state set to Disconnected for $address")
                    }
                }
            }
        }
    }

    @Suppress("DEPRECATION") // Tysta varningen för den äldre writeDescriptor/value-användningen
    private fun enableNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        val cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        val descriptor = characteristic.getDescriptor(cccdUuid)
        if (descriptor == null) {
            Log.e("BookooBleClient", "CCCD descriptor not found for characteristic ${characteristic.uuid}")
            connectionState.value = BleConnectionState.Error("Could not enable notifications (descriptor missing)")
            handleDisconnect(gatt)
            return
        }

        try {
            // Aktivera notiser lokalt
            val notificationSet = gatt.setCharacteristicNotification(characteristic, true)
            if (!notificationSet) {
                Log.e("BookooBleClient", "Failed to enable notifications locally for ${characteristic.uuid}")
                connectionState.value = BleConnectionState.Error("Could not enable notifications (local)")
                handleDisconnect(gatt)
                return
            }

            // Skriv till descriptorn för att aktivera dem på enheten
            val writeResult: Int
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                writeResult = gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                // För äldre versioner
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE // .value är deprecated men fungerar
                writeResult = if (gatt.writeDescriptor(descriptor)) { // writeDescriptor är deprecated men fungerar
                    GATT_SUCCESS_COMPAT // Använd kompatibel konstant
                } else {
                    GATT_UNKNOWN_ERROR_COMPAT // Använd kompatibel konstant
                }
            }

            if (writeResult != GATT_SUCCESS_COMPAT) {
                Log.e("BookooBleClient", "Failed to write CCCD descriptor for ${characteristic.uuid}. Error: $writeResult")
                connectionState.value = BleConnectionState.Error("Could not enable notifications (CCCD write fail: $writeResult)")
                handleDisconnect(gatt)
            } else {
                Log.i("BookooBleClient", "CCCD descriptor write initiated successfully for ${characteristic.uuid}")
            }
        } catch (e: SecurityException) {
            Log.e("BookooBleClient", "Bluetooth permission missing when enabling notifications", e)
            connectionState.value = BleConnectionState.Error("Bluetooth permission missing for notifications")
            handleDisconnect(gatt)
        }
    }

    @Suppress("DEPRECATION") // Tysta varningen för data.getOrNull(...) som använder .value internt? (Eller bara allmän ByteArray-access)
    private fun parseMeasurement(data: ByteArray): ScaleMeasurement? {
        // Vi behöver åtminstone upp till index 9 (BYTE 10) och rätt header
        if (data.size < 10 || data.getOrNull(0) != 0x03.toByte() || data.getOrNull(1) != 0x0B.toByte()) {
            Log.w("BookooBleClient", "Invalid or too short package: ${data.toHexString()}")
            return null
        }

        try {
            // --- Parse Weight (som tidigare) ---
            val signByte = data[6].toInt() and 0xFF // BYTE 7
            val isNegative = (signByte == 0x2D) // '-'
            val wH = data[7].toInt() and 0xFF // BYTE 8
            val wM = data[8].toInt() and 0xFF // BYTE 9
            val wL = data[9].toInt() and 0xFF // BYTE 10
            var rawWeight = (wH shl 16) or (wM shl 8) or wL
            if (isNegative) rawWeight = -rawWeight
            val grams = rawWeight / 100f

            // --- Parse Flow Rate ---
            var flowRate = 0.0f
            if (data.size >= 13) {
                val flowSignByte = data[10].toInt() and 0xFF // BYTE 11
                val isFlowNegative = (flowSignByte == 0x2D)
                val fH = data[11].toInt() and 0xFF // BYTE 12
                val fL = data[12].toInt() and 0xFF // BYTE 13
                var rawFlow = (fH shl 8) or fL
                if (isFlowNegative) rawFlow = -rawFlow
                flowRate = rawFlow / 100.0f
            }
            return ScaleMeasurement(grams, flowRate)
        } catch (e: ArrayIndexOutOfBoundsException) {
            Log.e("BookooBleClient", "Error parsing measurement due to unexpected data length: ${data.toHexString()}", e)
            return null
        }
    }

    private fun ByteArray.toHexString(): String = joinToString(separator = " ") { "%02x".format(it) }

    companion object {
        val BOOKOO_SERVICE_UUID: UUID = UUID.fromString("00000FFE-0000-1000-8000-00805f9B34FB")
        val WEIGHT_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000FF11-0000-1000-8000-00805f9b34fb")
        val COMMAND_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000FF12-0000-1000-8000-00805f9b34fb")
    }
}