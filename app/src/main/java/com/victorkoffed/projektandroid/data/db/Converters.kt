package com.victorkoffed.projektandroid.data.db

import androidx.room.TypeConverter
import java.util.Date

/**
 * Typekonverterare för Room.
 * Hanterar konvertering mellan komplexa Kotlin-objekt (som Date)
 * och primitiva typer (som Long/SQLite timestamp) för lagring i databasen.
 */
class Converters {
    /** Konverterar en Long (tidsstämpel) till ett Date-objekt. */
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    /** Konverterar ett Date-objekt till en Long (tidsstämpel). */
    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
}