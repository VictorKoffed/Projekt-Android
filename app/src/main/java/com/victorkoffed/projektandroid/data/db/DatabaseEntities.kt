package com.victorkoffed.projektandroid.data.db

import androidx.room.ColumnInfo
import androidx.room.DatabaseView
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

// Din SQL "PRAGMA foreign_keys = ON;" hanteras av @ForeignKey-annotationerna.

/**
 * 1) Grinder
 * Lagrar information om en kaffekvarn.
 */
@Entity(
    tableName = "Grinder",
    indices = [Index(value = ["name"], unique = true)] // Säkerställer unika namn
)
data class Grinder(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "grinder_id")
    val id: Long = 0,

    val name: String,
    val notes: String?
)

/**
 * 2) Method
 * Lagrar bryggmetoder (t.ex. "V60", "Aeropress").
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
 * 3) Bean
 * Lagrar information om en kaffeböna/påse.
 */
@Entity(
    tableName = "Bean",
    indices = [Index(value = ["remaining_weight_g"])] // Index för att snabbt hitta bönor
)
data class Bean(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "bean_id")
    val id: Long = 0,

    val name: String,
    val roaster: String?,

    @ColumnInfo(name = "roast_date") // Vi använder en TypeConverter för att lagra Date som Long
    val roastDate: Date?,

    @ColumnInfo(name = "initial_weight_g")
    val initialWeightGrams: Double?,

    @ColumnInfo(name = "remaining_weight_g")
    val remainingWeightGrams: Double = 0.0,

    val notes: String?
)

/**
 * 4) Brew
 * Lagrar en enskild bryggnings-session.
 */
@Entity(
    tableName = "Brew",
    foreignKeys = [
        ForeignKey(
            entity = Bean::class,
            parentColumns = ["bean_id"],
            childColumns = ["bean_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.RESTRICT // Skyddar brygghistorik
        ),
        ForeignKey(
            entity = Grinder::class,
            parentColumns = ["grinder_id"],
            childColumns = ["grinder_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.SET_NULL // Nollställer om kvarn raderas
        ),
        ForeignKey(
            entity = Method::class,
            parentColumns = ["method_id"],
            childColumns = ["method_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.SET_NULL // Nollställer om metod raderas
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
    val startedAt: Date = Date(), // Sätts automatiskt till "nu"

    val notes: String?,

    // --- NYTT FÄLT ---
    @ColumnInfo(name = "image_uri")
    val imageUri: String? = null
    // --- SLUT NYTT FÄLT ---
)

/**
 * 4b) BrewSample
 * Lagrar rådatan (tid/massa) från vågen för en specifik bryggning.
 */
@Entity(
    tableName = "BrewSample",
    foreignKeys = [
        ForeignKey(
            entity = Brew::class,
            parentColumns = ["brew_id"],
            childColumns = ["brew_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE // Raderar samples om bryggning raderas
        )
    ],
    indices = [Index(value = ["brew_id", "t_ms"])] // Supersnabbt att hämta grafdata
)
data class BrewSample(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "sample_id")
    val id: Long = 0,

    @ColumnInfo(name = "brew_id")
    val brewId: Long,

    @ColumnInfo(name = "t_ms")
    val timeMillis: Long, // Millisekunder från start

    @ColumnInfo(name = "mass_g")
    val massGrams: Double, // Kumulativ massa

    // --- UPPDATERAD DEL ---
    @ColumnInfo(name = "flow_rate_gs")
    val flowRateGramsPerSecond: Double?
    // --- SLUT PÅ UPPDATERING ---
)

/**
 * View: BrewMetrics
 * En virtuell tabell som beräknar ratio och totalt vatten.
 * Detta motsvarar din CREATE VIEW-sats.
 */
@DatabaseView("""
    SELECT
        b.brew_id,
        b.dose_g AS doseGrams,
        -- FIX: Hämta mass_g från det sample som har högst t_ms (det sista)
        (
            SELECT s.mass_g 
            FROM BrewSample s 
            WHERE s.brew_id = b.brew_id 
            ORDER BY s.t_ms DESC 
            LIMIT 1
        ) AS waterUsedGrams,
        CASE 
            WHEN b.dose_g > 0 THEN (
                -- Använd samma subquery för att beräkna ration
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