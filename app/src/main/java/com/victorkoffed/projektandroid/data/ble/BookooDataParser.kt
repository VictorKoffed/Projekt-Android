package com.victorkoffed.projektandroid.data.ble

import android.util.Log
import com.victorkoffed.projektandroid.domain.model.ScaleMeasurement
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Singleton object för att hantera parsning av rådata (ByteArray) från Bookoo BLE-vågen
 * till domänmodellen ScaleMeasurement.
 * Isolerar den komplexa byte-manipulationen från BLE-klienten.
 */
object BookooDataParser {
    private const val TAG = "BookooDataParser"

    /**
     * Parsar rådata från vågen till ett ScaleMeasurement-objekt.
     * Paketet förväntas ha en minsta längd på 14 bytes för att inkludera batteri.
     *
     * @param data Rådata från BluetoothGattCharacteristic.
     * @return ScaleMeasurement om parsning lyckas, annars null.
     */
    fun parseMeasurement(data: ByteArray): ScaleMeasurement? {
        // Kontrollera grundläggande paketlängd och header (0x03 0x0B)
        if (data.size < 14 || data.getOrNull(0) != 0x03.toByte() || data.getOrNull(1) != 0x0B.toByte()) {
            return null
        }

        try {
            // Tid (3 bytes, Index 2-4)
            val msH = data[2].toInt() and 0xFF
            val msM = data[3].toInt() and 0xFF
            val msL = data[4].toInt() and 0xFF
            val scaleTimeMillis = ((msH shl 16) or (msM shl 8) or msL).toLong()

            // Vikt (3 bytes, Index 7-9)
            val weightSign = data[6].toInt() and 0xFF
            val isWeightNegative = weightSign == 0x2D // 0x2D = ASCII '-'

            val wH = data[7].toInt() and 0xFF
            val wM = data[8].toInt() and 0xFF
            val wL = data[9].toInt() and 0xFF
            var rawWeight = (wH shl 16) or (wM shl 8) or wL
            if (isWeightNegative && rawWeight != 0) rawWeight = -rawWeight
            val grams = rawWeight.toFloat() / 100.0f

            // Flöde (2 bytes, Index 11-12)
            var flow = 0.0f
            if (data.size >= 13) {
                val flowSign = data[10].toInt() and 0xFF
                val isFlowNegative = flowSign == 0x2D

                val fH = data[11].toInt() and 0xFF
                val fL = data[12].toInt() and 0xFF
                var rawFlow = (fH shl 8) or fL
                if (isFlowNegative && rawFlow != 0) rawFlow = -rawFlow
                flow = rawFlow.toFloat() / 100.0f
            }

            // Batteri (1 byte, Index 13)
            val battery: Int? = if (data.size >= 14) {
                data[13].toInt() and 0xFF // Byte 14 (index 13)
            } else {
                null
            }

            return ScaleMeasurement(grams, flow, scaleTimeMillis, battery)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse measurement data.", e)
            return null
        }
    }

    private fun ByteArray.toHexString(): String = joinToString(separator = " ") { "%02x".format(it) }
}