/**
 * EXTERNAL PROTOCOL: Byte packet parsing logic strictly adheres to the data format
 * defined in the Bookoo BLE Protocol.
 * Source: https://github.com/BooKooCode/OpenSource/blob/main/bookoo_mini_scale/protocols.md
 */

package com.victorkoffed.projektandroid.data.ble

import android.util.Log
import com.victorkoffed.projektandroid.domain.model.ScaleMeasurement

/**
 * Parses raw byte arrays from the Bookoo BLE scale into the [ScaleMeasurement] domain model.
 * Isolates protocol-specific byte manipulation and bit-shifting from the BLE communication layer.
 */
object BookooDataParser {
    private const val TAG = "BookooDataParser"

    /**
     * Converts a raw data packet into a domain model.
     * Enforces a minimum length of 14 bytes to ensure battery data is included,
     * and validates the required protocol header (0x03 0x0B).
     *
     * @param data Raw byte array from the BluetoothGattCharacteristic.
     * @return Parsed [ScaleMeasurement], or null if the packet is malformed or incomplete.
     */
    fun parseMeasurement(data: ByteArray): ScaleMeasurement? {
        if (data.size < 14 || data.getOrNull(0) != 0x03.toByte() || data.getOrNull(1) != 0x0B.toByte()) {
            return null
        }

        try {
            val msH = data[2].toInt() and 0xFF
            val msM = data[3].toInt() and 0xFF
            val msL = data[4].toInt() and 0xFF
            val scaleTimeMillis = ((msH shl 16) or (msM shl 8) or msL).toLong()

            // The protocol encodes signs using ASCII characters; 0x2D corresponds to '-'
            val weightSign = data[6].toInt() and 0xFF
            val isWeightNegative = weightSign == 0x2D

            val wH = data[7].toInt() and 0xFF
            val wM = data[8].toInt() and 0xFF
            val wL = data[9].toInt() and 0xFF
            var rawWeight = (wH shl 16) or (wM shl 8) or wL
            if (isWeightNegative && rawWeight != 0) rawWeight = -rawWeight
            val grams = rawWeight.toFloat() / 100.0f

            var flow: Float
            val flowSign = data[10].toInt() and 0xFF
            val isFlowNegative = flowSign == 0x2D

            val fH = data[11].toInt() and 0xFF
            val fL = data[12].toInt() and 0xFF
            var rawFlow = (fH shl 8) or fL
            if (isFlowNegative && rawFlow != 0) rawFlow = -rawFlow
            flow = rawFlow.toFloat() / 100.0f

            val battery: Int = data[13].toInt() and 0xFF

            return ScaleMeasurement(grams, flow, scaleTimeMillis, battery)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse measurement data.", e)
            return null
        }
    }
}