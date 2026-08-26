package com.victorkoffed.projektandroid.data.db

import androidx.room.TypeConverter
import java.util.Date

/**
 * Provides serialization strategies for non-primitive domain types used across the database entities.
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