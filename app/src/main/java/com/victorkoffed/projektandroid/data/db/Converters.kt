package com.victorkoffed.projektandroid.data.db

import androidx.room.TypeConverter
import java.util.Date

/**
 * Konverterar komplexa typer (som Date) till primitiva typer (som Long)
 * som SQLite förstår hur man lagrar.
 */
class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
}
