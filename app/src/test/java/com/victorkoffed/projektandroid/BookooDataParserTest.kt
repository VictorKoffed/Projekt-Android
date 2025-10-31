package com.victorkoffed.projektandroid

import com.victorkoffed.projektandroid.data.ble.BookooDataParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Enhetstester för BookooDataParser.
 * Validerar att råa byte-paket från vågen parsas korrekt till ScaleMeasurement-objekt.
 */
class BookooDataParserTest {

    // Delta-värde för flyttalsjämförelser för att hantera precision.
    private val fLOATDELTA = 0.01f

    @Test
    fun parseMeasurement_validPositiveData_returnsCorrectMeasurement() {
        // Testpaket: 76580ms (0x012B24), 341.2g (0x008548), 0.0g/s (0x0000), 80% (0x50)
        // Header: 0x03, 0x0B (Index 0, 1)
        // Tid (3 bytes): 0x01, 0x2B, 0x24 (Index 2-4)
        // Vikt Sign: 0x00 (Index 6)
        // Vikt (3 bytes): 0x00, 0x85, 0x48 (Index 7-9)
        // Flöde Sign: 0x00 (Index 10)
        // Flöde (2 bytes): 0x00, 0x00 (Index 11-12)
        // Batteri (1 byte): 0x50 (Index 13)

        val data = byteArrayOf(
            0x03, 0x0B, 0x01, 0x2B, 0x24, 0x00, 0x00, 0x00, 0x85.toByte(), 0x48, 0x00, 0x00, 0x00, 0x50
        )
        val result = BookooDataParser.parseMeasurement(data)

        // Asserts
        // Vi måste använda 'abs' (absolute value) för att hantera flyttalsjämförelser i Kotlin/JUnit,
        // vilket är analogt med det flyttalsjämförelseargument du använder i XUnit.

        // Tid (76580 ms)
        assertEquals(76580L, result?.timeMillis)

        // Vikt (341.2f)
        // Flyttalsjämförelse med delta (341.2f - result.weightGrams < DELTA)
        assertEquals(341.2f, result?.weightGrams ?: 0f, fLOATDELTA)

        // Flöde (0.0f)
        assertEquals(0.0f, result?.flowRateGramsPerSecond ?: 1f, fLOATDELTA)

        // Batteri (80)
        assertEquals(80, result?.batteryPercent)
    }

    @Test
    fun parseMeasurement_negativeWeightData_returnsCorrectMeasurement() {
        // Testpaket: 0ms (0x000000), -50.0g (0x001388), 0.0g/s, 80%
        // Vikt Sign: 0x2D ('-') (Index 6)
        val data = byteArrayOf(
            0x03, 0x0B, 0x00, 0x00, 0x00, 0x00, 0x2D, 0x00, 0x13, 0x88.toByte(), 0x00, 0x00, 0x00, 0x50
        )
        val result = BookooDataParser.parseMeasurement(data)

        // Vikt (-50.0f)
        assertEquals(-50.0f, result?.weightGrams ?: 0f, fLOATDELTA)

        // Batteri
        assertEquals(80, result?.batteryPercent)

        // Tid (0L)
        assertEquals(0L, result?.timeMillis)
    }

    @Test
    fun parseMeasurement_negativeFlowData_returnsCorrectMeasurement() {
        // Testpaket: 0ms (0x000000), 0.0g (0x000000), -1.23g/s (0x007B), 80%
        // Flöde Sign: 0x2D ('-') (Index 10)
        // Flöde: 123 raw (0x7B)
        val data = byteArrayOf(
            0x03, 0x0B, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x2D, 0x00, 0x7B, 0x50
        )
        val result = BookooDataParser.parseMeasurement(data)

        // Vikt (0.0f)
        assertEquals(0.0f, result?.weightGrams ?: 0f, fLOATDELTA)

        // Flöde (-1.23f)
        assertEquals(-1.23f, result?.flowRateGramsPerSecond ?: 1f, fLOATDELTA)
    }

    @Test
    fun parseMeasurement_invalidHeader_returnsNull() {
        // Felaktig header (ska vara 0x03, 0x0B)
        val data = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E)
        val result = BookooDataParser.parseMeasurement(data)
        assertNull(result)
    }

    @Test
    fun parseMeasurement_shortArray_returnsNull() {
        // För kort array (ska vara minst 14 bytes)
        val data = byteArrayOf(0x03, 0x0B, 0x01, 0x02, 0x03)
        val result = BookooDataParser.parseMeasurement(data)
        assertNull(result)
    }
}