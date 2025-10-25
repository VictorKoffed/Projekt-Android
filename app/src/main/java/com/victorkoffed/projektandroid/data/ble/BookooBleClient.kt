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

// Konstanter för att hantera skillnader i API-värden (främst från API 31+)
private const val GATT_SUCCESS_COMPAT = 0
private const val GATT_UNKNOWN_ERROR_COMPAT = -1

@SuppressLint("MissingPermission")
class BookooBleClient(private val context: Context) {

    // --- Flows för att exponera anslutningsstatus och mätdata ---
    // StateFlow håller aktuell anslutningsstatus (t.ex. Connected, Disconnected, Error)
    val connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
    // SharedFlow för att skicka mätningar i realtid
    val measurements = MutableSharedFlow<ScaleMeasurement>()

    // --- Bluetooth-relaterade fält (lazy-initialisering) ---
    private val btManager by lazy { context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager }
    private val btAdapter by lazy { btManager.adapter }
    private val scanner by lazy { btAdapter.bluetoothLeScanner }
    private var gatt: BluetoothGatt? = null // Håller den aktiva GATT-anslutningen
    // Används för att köra BLE-operationer på huvudtråden, vilket ofta krävs för GATT
    private val mainHandler = Handler(Looper.getMainLooper())
    // CoroutineScope för bakgrundsoperationer, t.ex. att emitera data eller lägga till delay.
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    // --- GATT Callback: Hanterar alla asynkrona svar från BLE-enheten ---
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val deviceAddress = gatt.device.address
            if (status == BluetoothGatt.GATT_SUCCESS) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Log.i("BookooBleClient", "Successfully connected to $deviceAddress")
                        // Sätter till Connecting i väntan på service discovery
                        connectionState.value = BleConnectionState.Connecting
                        // Starta service discovery. Måste köras på huvudtråden (vissa Android-versioner).
                        mainHandler.post {
                            this@BookooBleClient.gatt?.discoverServices()
                        }
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.i("BookooBleClient", "Successfully disconnected from $deviceAddress")
                        handleDisconnect(gatt) // Rensa resurser och uppdatera state
                    }
                }
            } else {
                // Fel uppstod under anslutning/frånkoppling
                Log.e("BookooBleClient", "GATT Error on connection state change for $deviceAddress. Status: $status, New state: $newState")
                // Sätt Error state FÖRE cleanup så UI kan reagera på felet
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
                    // Byt till Connected om vi fortfarande är i Connecting-läge
                    if (connectionState.value is BleConnectionState.Connecting) {
                        connectionState.value = BleConnectionState.Connected(gatt.device.name ?: gatt.device.address)
                        Log.i("BookooBleClient", "Services discovered and notifications enabled for ${gatt.device.address}")
                    }
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

        @Suppress("DEPRECATION") // Använder den gamla versionen av onCharacteristicChanged, men hanterar deprecation i funktionen nedan
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == WEIGHT_CHARACTERISTIC_UUID) {
                // Notifiering mottagen - parsning och emit till Flow
                parseMeasurement(characteristic.value)?.let {
                    // Starta en coroutine för att emitera datan från IO-tråden till Flow
                    scope.launch { measurements.emit(it) }
                }
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("BookooBleClient", "Command sent!")
            } else {
                Log.e("BookooBleClient", "Failed to send command, status: $status")
            }
        }
    }

    // --- Public API ---

    /**
     * Startar en BLE-skanning och exponerar resultat som en Flow.
     * Skanningen stoppas automatiskt när Flow-konsumenten avbryter.
     */
    fun startScan(): Flow<ScanResult> = callbackFlow {
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) { trySend(result) }
            override fun onScanFailed(errorCode: Int) { close(IllegalStateException("BLE scan failed with code: $errorCode")) }
        }
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()

        if (btAdapter?.isEnabled == true) {
            scanner.startScan(null, settings, callback)
        } else {
            close(IllegalStateException("Bluetooth is not enabled."))
        }

        // awaitClose blocket körs när Flow-konsumenten avbryter (slutar samla data)
        awaitClose {
            if (btAdapter?.isEnabled == true) {
                scanner?.stopScan(callback)
            }
        }
    }

    /**
     * Initierar anslutning till en BLE-enhet via MAC-adress.
     * Inkluderar logik för att undvika dubbla anslutningsförsök och race conditions.
     */
    fun connect(address: String) {
        if (connectionState.value is BleConnectionState.Connected && gatt?.device?.address == address) {
            Log.w("BookooBleClient", "Already connected to $address. Ignoring connect request.")
            return
        }
        if (connectionState.value is BleConnectionState.Connecting) {
            Log.w("BookooBleClient", "Connection attempt already in progress. Ignoring connect request to $address.")
            return
        }

        Log.i("BookooBleClient", "Attempting to connect to $address...")

        // Steg 1: Rensa upp eventuell gammal GATT-instans (postar Disconnected event)
        handleDisconnect(gatt)

        // Steg 2: Posta Connecting-state till huvudtråden. Detta event kommer att exekveras EFTER det Disconnected-event
        // som postades i Steg 1, vilket garanterar att vår Connecting-state är den sista på tråden innan anslutning.
        mainHandler.post {
            connectionState.value = BleConnectionState.Connecting
        }

        // Steg 3: Starta själva anslutningsprocessen på en IO-tråd med fördröjning
        scope.launch {
            try {
                val device = btAdapter.getRemoteDevice(address)

                // Fördröjning för att ge tid för GATT-cleanup att slutföras i OS-lagret.
                delay(1000L)

                // Skapa en NY gatt-instans (connectGatt returnerar null om fel uppstår)
                val newGatt = device.connectGatt(
                    context,
                    false, // autoConnect = false
                    gattCallback,
                    BluetoothDevice.TRANSPORT_LE // Explicit LE transport
                )

                // Steg 4: Uppdatera 'gatt' variabeln på huvudtråden (trådsäkert)
                mainHandler.post {
                    // Vi kan nu anta att om state är Connecting, är det vår *nya* Connecting state (från steg 2)
                    // och att vi avser att ansluta. Den gamla checken är inte längre nödvändig men säkrar mot
                    // andra oväntade tillstånd.
                    if (connectionState.value is BleConnectionState.Connecting) {
                        gatt = newGatt
                        if (gatt == null) {
                            Log.e("BookooBleClient", "device.connectGatt returned null for $address.")
                            connectionState.value = BleConnectionState.Error("Could not initiate connection")
                        } else {
                            Log.d("BookooBleClient", "connectGatt called successfully for $address. Waiting for callback...")
                        }
                    } else {
                        // Om state ändrats under coroutinen (t.ex. avbruten manuellt), stäng den nya GATT-instansen
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
                handleDisconnect(gatt)
            }
        }
    }

    /**
     * Stänger den nuvarande GATT-anslutningen och städar upp.
     */
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
        handleDisconnect(currentGatt) // Återanvänd cleanup-logiken
    }

    /**
     * Skickar en "tare" (nollställning) kommando till vågen.
     * Använder versionsspecifik logik för writeCharacteristic.
     */
    @Suppress("DEPRECATION")
    fun sendTareCommand() {
        val commandChar = gatt?.getService(BOOKOO_SERVICE_UUID)?.getCharacteristic(COMMAND_CHARACTERISTIC_UUID)
        if (gatt == null || commandChar == null) {
            Log.e("BookooBleClient", "Cannot send tare command: gatt or command characteristic not found.")
            return
        }
        val tareCommand = byteArrayOf(0x03, 0x0A, 0x01, 0x00, 0x00, 0x08)

        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Använd det moderna, icke-deprecated API:et
            gatt?.writeCharacteristic(commandChar, tareCommand, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) ?: GATT_UNKNOWN_ERROR_COMPAT
        } else {
            // Använd det deprecated API:et för äldre versioner
            commandChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            commandChar.value = tareCommand
            if (gatt?.writeCharacteristic(commandChar) == true) {
                GATT_SUCCESS_COMPAT
            } else {
                GATT_UNKNOWN_ERROR_COMPAT
            }
        }

        if (result == GATT_SUCCESS_COMPAT) {
            Log.d("BookooBleClient", "Tare command write operation initiated successfully.")
        } else {
            Log.e("BookooBleClient", "Failed to initiate tare command write operation. Error code: $result")
        }
    }


    // --- Privata Hjälpfunktioner ---

    /**
     * Stänger anslutningen, städar upp GATT-resurser och uppdaterar anslutningsstatus.
     * Måste kallas vid både framgångsrik frånkoppling och fel.
     * @param gattInstance den specifika GATT-instansen att hantera (kan vara gatt eller en nygammal)
     */
    private fun handleDisconnect(gattInstance: BluetoothGatt?) {
        val address = gattInstance?.device?.address ?: "unknown device"
        Log.d("BookooBleClient", "Handling disconnect/cleanup for $address")
        try {
            gattInstance?.disconnect() // Skickar signal till enheten
            gattInstance?.close() // Frigör resurser i Android-stacken
        } catch (e: SecurityException) {
            Log.e("BookooBleClient", "Bluetooth permission missing under cleanup for $address", e)
        } catch (e: Exception) {
            Log.e("BookooBleClient", "Exception during GATT cleanup for $address", e)
        } finally {
            // Nolla den aktuella instansen om den matchar den vi städar upp
            if (this.gatt == gattInstance) {
                this.gatt = null
                Log.d("BookooBleClient", "Current gatt instance nulled for $address")
            }
            // Uppdatera state till Disconnected (trådsäkert via mainHandler)
            mainHandler.post {
                if (connectionState.value !is BleConnectionState.Disconnected) {
                    connectionState.value = BleConnectionState.Disconnected
                    Log.i("BookooBleClient", "Connection state set to Disconnected for $address")
                }
            }
        }
    }

    /**
     * Aktiverar notifications/indications på en specifik karakteristik.
     * Inkluderar versionsspecifik skrivning till CCCD-descriptorn (0x2902).
     */
    @Suppress("DEPRECATION")
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
            // Steg 1: Aktivera notiser lokalt i Android-stacken
            val notificationSet = gatt.setCharacteristicNotification(characteristic, true)
            if (!notificationSet) {
                Log.e("BookooBleClient", "Failed to enable notifications locally for ${characteristic.uuid}")
                connectionState.value = BleConnectionState.Error("Could not enable notifications (local)")
                handleDisconnect(gatt)
                return
            }

            // Steg 2: Skriv till CCCD-descriptorn för att aktivera notiser på BLE-enheten
            val writeResult: Int
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Använd det moderna, icke-deprecated API:et
                writeResult = gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                // Använd det deprecated API:et för äldre versioner
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                writeResult = if (gatt.writeDescriptor(descriptor)) {
                    GATT_SUCCESS_COMPAT
                } else {
                    GATT_UNKNOWN_ERROR_COMPAT
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

    /**
     * Parasar rådata (ByteArray) från vågens karakteristik till ett ScaleMeasurement-objekt.
     * Har specifik logik för att hantera vågens protokoll (t.ex. byte-positioner, teckenhantering).
     */
    @Suppress("DEPRECATION")
    private fun parseMeasurement(data: ByteArray): ScaleMeasurement? {
        // Kontrollera minsta längd och enhetens datapaket-header (0x03, 0x0B)
        if (data.size < 10 || data.getOrNull(0) != 0x03.toByte() || data.getOrNull(1) != 0x0B.toByte()) {
            Log.w("BookooBleClient", "Invalid or too short package, expected header [03 0B]: ${data.toHexString()}")
            return null
        }

        try {
            // --- Parsar vikt (Bytes 7-10) ---
            val signByte = data[6].toInt() and 0xFF
            val isNegative = (signByte == 0x2D) // Kollar om tecknet är '-'
            val wH = data[7].toInt() and 0xFF
            val wM = data[8].toInt() and 0xFF
            val wL = data[9].toInt() and 0xFF
            var rawWeight = (wH shl 16) or (wM shl 8) or wL
            if (isNegative) rawWeight = -rawWeight
            val grams = rawWeight / 100f // Vågen skickar vikten i 0.01g enheter

            // --- Parsar flödeshastighet (Bytes 11-13) ---
            var flowRate = 0.0f
            if (data.size >= 13) {
                val flowSignByte = data[10].toInt() and 0xFF
                val isFlowNegative = (flowSignByte == 0x2D)
                val fH = data[11].toInt() and 0xFF
                val fL = data[12].toInt() and 0xFF
                var rawFlow = (fH shl 8) or fL
                if (isFlowNegative) rawFlow = -rawFlow
                flowRate = rawFlow / 100.0f // Flödeshastighet i 0.01 enheter
            }
            return ScaleMeasurement(grams, flowRate)
        } catch (e: ArrayIndexOutOfBoundsException) {
            Log.e("BookooBleClient", "Error parsing measurement due to unexpected data length: ${data.toHexString()}", e)
            return null
        }
    }

    /**
     * Extension-funktion för att konvertera ByteArray till en läsbar hexadecimal sträng.
     */
    private fun ByteArray.toHexString(): String = joinToString(separator = " ") { "%02x".format(it) }

    // --- Bluetooth Service och Characteristic UUID:er ---
    companion object {
        val BOOKOO_SERVICE_UUID: UUID = UUID.fromString("00000FFE-0000-1000-8000-00805f9B34FB")
        val WEIGHT_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000FF11-0000-1000-8000-00805f9b34fb")
        val COMMAND_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000FF12-0000-1000-8000-00805f9b34fb")
    }
}