/**
 * Implementation Note: The [BrewMetrics] database view utilizes SQLite subqueries
 * to offload real-time telemetry aggregations (such as brew ratio and total water used).
 * This architectural decision prevents loading massive time-series datasets into application memory.
 */

package com.victorkoffed.projektandroid.data.db

import androidx.room.ColumnInfo
import androidx.room.DatabaseView
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Represents a specific coffee grinder used in a brew session.
 */
@Entity(
    tableName = "Grinder",
    indices = [Index(value = ["name"], unique = true)]
)
data class Grinder(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "grinder_id")
    val id: Long = 0,

    val name: String,
    val notes: String?
)

/**
 * Defines a specific brewing method (e.g., V60, Aeropress).
 */
@Entity(
    tableName = "Method",
    indices = [Index(value = ["name"], unique = true)]
)
data class Method(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "method_id")
    val id: Long = 0,

    val name: String
)

/**
 * Represents a specific batch of coffee beans.
 * Acts as an inventory tracker; the remaining weight is continuously updated
 * to reflect stock as new brews are recorded.
 */
@Entity(
    tableName = "Bean",
    indices = [Index(value = ["remaining_weight_g"])]
)
data class Bean(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "bean_id")
    val id: Long = 0,

    val name: String,
    val roaster: String?,

    @ColumnInfo(name = "roast_date")
    val roastDate: Date?,

    @ColumnInfo(name = "initial_weight_g")
    val initialWeightGrams: Double?,

    @ColumnInfo(name = "remaining_weight_g")
    val remainingWeightGrams: Double = 0.0,

    /** Soft-delete flag. Hides the bean from active selection without breaking historical brew records. */
    @ColumnInfo(name = "is_archived")
    val isArchived: Boolean = false,

    val notes: String?
)

/**
 * Represents a single coffee brewing session and its specific parameters.
 * Ties together the bean, equipment, and final configuration used.
 */
@Entity(
    tableName = "Brew",
    foreignKeys = [
        ForeignKey(
            entity = Bean::class,
            parentColumns = ["bean_id"],
            childColumns = ["bean_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Grinder::class,
            parentColumns = ["grinder_id"],
            childColumns = ["grinder_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = Method::class,
            parentColumns = ["method_id"],
            childColumns = ["method_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["bean_id"]),
        Index(value = ["grinder_id"]),
        Index(value = ["method_id"])
    ]
)
data class Brew(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "brew_id")
    val id: Long = 0,

    @ColumnInfo(name = "bean_id")
    val beanId: Long,

    @ColumnInfo(name = "grinder_id")
    val grinderId: Long?,

    @ColumnInfo(name = "method_id")
    val methodId: Long?,

    @ColumnInfo(name = "dose_g")
    val doseGrams: Double,

    @ColumnInfo(name = "grind_setting")
    val grindSetting: String?,

    @ColumnInfo(name = "grind_speed_rpm")
    val grindSpeedRpm: Double?,

    @ColumnInfo(name = "brew_temp_c")
    val brewTempCelsius: Double?,

    @ColumnInfo(name = "started_at")
    val startedAt: Date = Date(),

    val notes: String?,

    @ColumnInfo(name = "image_uri")
    val imageUri: String? = null
)

/**
 * Represents a single telemetry data point from the smart scale during an active brew.
 * Forms a time-series dataset for charting and brew analysis.
 */
@Entity(
    tableName = "BrewSample",
    foreignKeys = [
        ForeignKey(
            entity = Brew::class,
            parentColumns = ["brew_id"],
            childColumns = ["brew_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["brew_id", "t_ms"])]
)
data class BrewSample(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "sample_id")
    val id: Long = 0,

    @ColumnInfo(name = "brew_id")
    val brewId: Long,

    @ColumnInfo(name = "t_ms")
    val timeMillis: Long,

    @ColumnInfo(name = "mass_g")
    val massGrams: Double,

    @ColumnInfo(name = "flow_rate_gs")
    val flowRateGramsPerSecond: Double?
)

/**
 * Virtual table that calculates key brew performance indicators.
 * Aggregations are handled at the database level to optimize memory consumption.
 */
@DatabaseView("""
    SELECT
        b.brew_id,
        b.dose_g AS doseGrams,
        (
            SELECT s.mass_g 
            FROM BrewSample s 
            WHERE s.brew_id = b.brew_id 
            ORDER BY s.t_ms DESC 
            LIMIT 1
        ) AS waterUsedGrams,
        CASE 
            WHEN b.dose_g > 0 THEN (
                SELECT s.mass_g 
                FROM BrewSample s 
                WHERE s.brew_id = b.brew_id 
                ORDER BY s.t_ms DESC 
                LIMIT 1
            ) / b.dose_g 
            ELSE NULL 
        END AS ratio
    FROM Brew b
    GROUP BY b.brew_id
""")
data class BrewMetrics(
    @ColumnInfo(name = "brew_id")
    val brewId: Long,
    val doseGrams: Double,
    val waterUsedGrams: Double,
    val ratio: Double?
)