/*
 * Referensnotering (AI-assistans): Definitionen av den komplexa @DatabaseView
 * BrewMetrics (som beräknar ratio och waterUsedGrams med Room och SQL-subqueries)
 * har strukturerats med AI-assistans. Se README.md.
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
 * 1) Grinder (Kvarn)
 * Lagrar information om en specifik kaffekvarn.
 */
@Entity(
    tableName = "Grinder",
    // Skapar ett unikt index på namnet för att förhindra dubbletter.
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
 * 2) Method (Bryggmetod)
 * Lagrar namnen på bryggmetoder (t.ex. "V60", "Aeropress").
 */
@Entity(
    tableName = "Method",
    // Säkerställer unika metoder.
    indices = [Index(value = ["name"], unique = true)]
)
data class Method(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "method_id")
    val id: Long = 0,

    val name: String
)

/**
 * 3) Bean (Kaffeböna/Påse)
 * Lagrar information och lagersaldo för en specifik kaffeböna.
 */
@Entity(
    tableName = "Bean",
    // Index för snabb sökning/filtrering baserat på återstående vikt.
    indices = [Index(value = ["remaining_weight_g"])]
)
data class Bean(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "bean_id")
    val id: Long = 0,

    val name: String,
    val roaster: String?,

    @ColumnInfo(name = "roast_date")
    // Date konverteras till Long via Converters.kt.
    val roastDate: Date?,

    @ColumnInfo(name = "initial_weight_g")
    val initialWeightGrams: Double?,

    @ColumnInfo(name = "remaining_weight_g")
    // Standardvärdet garanterar att fältet aldrig är null i databasen.
    val remainingWeightGrams: Double = 0.0,

    // NYTT: Markerar om bönan är arkiverad (dold från huvudlistor)
    @ColumnInfo(name = "is_archived")
    val isArchived: Boolean = false,

    val notes: String?
)

/**
 * 4) Brew (Bryggning)
 * Lagrar en enskild bryggningssession och dess parametrar.
 */
@Entity(
    tableName = "Brew",
    foreignKeys = [
        // Relation till Bean. Om en böna raderas, ska bryggningen också raderas (CASCADE).
        ForeignKey(
            entity = Bean::class,
            parentColumns = ["bean_id"],
            childColumns = ["bean_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE
        ),
        // Relation till Grinder. Om kvarn raderas, sätts grinder_id till NULL (SET_NULL).
        ForeignKey(
            entity = Grinder::class,
            parentColumns = ["grinder_id"],
            childColumns = ["grinder_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.SET_NULL
        ),
        // Relation till Method. Om metod raderas, sätts method_id till NULL (SET_NULL).
        ForeignKey(
            entity = Method::class,
            parentColumns = ["method_id"],
            childColumns = ["method_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.SET_NULL
        )
    ],
    // Index på Foreign Keys för snabba JOINs
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

    // Lagrar en URI till bilden för den specifika bryggningen.
    @ColumnInfo(name = "image_uri")
    val imageUri: String? = null
)

/**
 * 4b) BrewSample (Mätpunkt)
 * Lagrar rådata (tid, massa, flöde) från vågen för en specifik bryggning.
 */
@Entity(
    tableName = "BrewSample",
    foreignKeys = [
        // Relation till Brew. Om bryggningen raderas, raderas alla samples (CASCADE).
        ForeignKey(
            entity = Brew::class,
            parentColumns = ["brew_id"],
            childColumns = ["brew_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE
        )
    ],
    // Sammansatt index för att snabbt hämta och sortera tidsseriedata.
    indices = [Index(value = ["brew_id", "t_ms"])]
)
data class BrewSample(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "sample_id")
    val id: Long = 0,

    @ColumnInfo(name = "brew_id")
    val brewId: Long,

    @ColumnInfo(name = "t_ms")
    val timeMillis: Long, // Tid i millisekunder från bryggningens start

    @ColumnInfo(name = "mass_g")
    val massGrams: Double, // Kumulativ massa i gram

    @ColumnInfo(name = "flow_rate_gs")
    val flowRateGramsPerSecond: Double? // Flödeshastighet i gram per sekund
)

/**
 * View: BrewMetrics (Beräknade Mått)
 * En virtuell tabell som beräknar nyckeltal som totalt vatten och ratio.
 * Används för att undvika komplexa beräkningar i Kotlin-koden.
 */
@DatabaseView("""
    SELECT
        b.brew_id,
        b.dose_g AS doseGrams,
        -- Subquery: Hämtar mass_g från den BrewSample som har högst t_ms för aktuell brew_id (sista mätningen).
        (
            SELECT s.mass_g 
            FROM BrewSample s 
            WHERE s.brew_id = b.brew_id 
            ORDER BY s.t_ms DESC 
            LIMIT 1
        ) AS waterUsedGrams,
        -- Beräknar ratio (vatten / dos). Returnerar NULL om dosen är noll för att undvika division med noll.
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
    -- Måste gruppera på brew_id eftersom vi använder en aggregeringsfunktion (subquery) per bryggning.
    GROUP BY b.brew_id
""")
data class BrewMetrics(
    @ColumnInfo(name = "brew_id")
    val brewId: Long,
    val doseGrams: Double,
    // total mängd vatten (eller kaffe) från sista mätpunkten.
    val waterUsedGrams: Double,
    // Vatten/Doserings-ratio.
    val ratio: Double?
)