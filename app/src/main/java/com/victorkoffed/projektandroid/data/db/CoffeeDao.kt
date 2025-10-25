package com.victorkoffed.projektandroid.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) för all lagring relaterad till kaffebryggning.
 * Room hanterar implementationen av detta interface.
 */
@Dao
interface CoffeeDao {
    // --- Grinder (Kvarn) ---

    /** Lägger till en ny kvarn. Ignorerar om den redan finns baserat på primärnyckel. */
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertGrinder(grinder: Grinder)
    @Update suspend fun updateGrinder(grinder: Grinder)
    @Delete suspend fun deleteGrinder(grinder: Grinder)
    /** Hämtar alla kvarnar, sorterade alfabetiskt, som en reaktiv Flow. */
    @Query("SELECT * FROM Grinder ORDER BY name ASC") fun getAllGrinders(): Flow<List<Grinder>>
    @Query("SELECT * FROM Grinder WHERE grinder_id = :id") suspend fun getGrinderById(id: Long): Grinder?

    // --- Method (Bryggmetod) ---

    /** Lägger till en ny metod. Ignorerar om den redan finns. */
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertMethod(method: Method)
    @Update suspend fun updateMethod(method: Method)
    @Delete suspend fun deleteMethod(method: Method)
    /** Hämtar alla metoder, sorterade alfabetiskt, som en reaktiv Flow. */
    @Query("SELECT * FROM Method ORDER BY name ASC") fun getAllMethods(): Flow<List<Method>>
    @Query("SELECT * FROM Method WHERE method_id = :id") suspend fun getMethodById(id: Long): Method?

    // --- Bean (Kaffeböna) ---

    /** Lägger till eller ersätter en bön-post. Ersätter vid konflikt (uppdaterar hela posten). */
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertBean(bean: Bean)
    @Update suspend fun updateBean(bean: Bean)
    @Delete suspend fun deleteBean(bean: Bean)
    /** Hämtar alla bönor, sorterade alfabetiskt, som en reaktiv Flow. */
    @Query("SELECT * FROM Bean ORDER BY name ASC") fun getAllBeans(): Flow<List<Bean>>
    @Query("SELECT * FROM Bean WHERE bean_id = :id") suspend fun getBeanById(id: Long): Bean?

    /** Ökar lagersaldot för en specifik böna. */
    @Query("UPDATE Bean SET remaining_weight_g = remaining_weight_g + :dose WHERE bean_id = :beanId")
    suspend fun incrementBeanStock(beanId: Long, dose: Double)

    /** Minskar lagersaldot för en specifik böna, säkerställer att saldot inte går under noll (MAX(0, ...)). */
    @Query("UPDATE Bean SET remaining_weight_g = MAX(0, remaining_weight_g - :dose) WHERE bean_id = :beanId")
    suspend fun decrementBeanStock(beanId: Long, dose: Double)

    // --- Brew (Bryggning) ---

    /** Lägger till en ny bryggning och returnerar den genererade ID:t. */
    @Insert suspend fun insertBrew(brew: Brew): Long
    @Update suspend fun updateBrew(brew: Brew)
    @Delete suspend fun deleteBrew(brew: Brew)
    @Query("SELECT * FROM Brew WHERE brew_id = :id") suspend fun getBrewById(id: Long): Brew?
    /** Hämtar alla bryggningar, sorterade efter starttid (senaste först), som en reaktiv Flow. */
    @Query("SELECT * FROM Brew ORDER BY started_at DESC") fun getAllBrews(): Flow<List<Brew>>

    /** Observerar en enskild bryggning reaktivt. */
    @Query("SELECT * FROM Brew WHERE brew_id = :id")
    fun observeBrew(id: Long): Flow<Brew?>

    /**
     * Transaktion för att radera en bryggning och återställa den använda bönmängden till lagret.
     * Denna garanterar atomicitet.
     */
    @Transaction
    suspend fun deleteBrewTransaction(brew: Brew) {
        // Steg 1: Återställ bönans lagersaldo innan radering.
        incrementBeanStock(brew.beanId, brew.doseGrams)
        // Steg 2: Radera bryggningen. (BrewSample raderas automatiskt via CASCADE-regel i databasen).
        deleteBrew(brew)
        // Steg 3: Uppdatera bönan manuellt. Detta garanterar att Flow<List<Bean>> uppdateras i UI.
        val bean = getBeanById(brew.beanId)
        if (bean != null) updateBean(bean)
    }

    /** Hämtar alla bryggningar associerade med en specifik böna, sorterade efter starttid (senaste först). */
    @Query("SELECT * FROM Brew WHERE bean_id = :beanId ORDER BY started_at DESC")
    fun getBrewsForBean(beanId: Long): Flow<List<Brew>>

    // --- BrewSample (Bryggdata-punkter) ---

    /** Lägger till en lista med mätpunkter (samples) för en bryggning. */
    @Insert suspend fun insertBrewSamples(samples: List<BrewSample>)

    /** Hämtar alla mätpunkter för en specifik bryggning, sorterade efter tid (t_ms). */
    @Query("SELECT * FROM BrewSample WHERE brew_id = :brewId ORDER BY t_ms ASC")
    fun getSamplesForBrew(brewId: Long): Flow<List<BrewSample>>

    // --- BrewMetrics (View: Beräknade Mått) ---

    /** Hämtar beräknade mätvärden (t.ex. totaltid, flöden) för en bryggning, exponerat som Flow. */
    @Query("SELECT * FROM BrewMetrics WHERE brew_id = :brewId")
    fun getBrewMetrics(brewId: Long): Flow<BrewMetrics?>

    // --- Transaktionella Hjälpfunktioner ---

    /**
     * Transaktion för att lägga till en ny bryggning tillsammans med dess mätpunkter.
     * Hanterar primärnyckelbindning och uppdaterar lagersaldo.
     */
    @Transaction
    suspend fun addBrewWithSamples(brew: Brew, samples: List<BrewSample>): Long {
        // Steg 1: Lägg till bryggningen och få dess ID
        val brewId = insertBrew(brew)
        // Steg 2: Koppla det nya ID:t till alla mätpunkter
        val samplesWithBrewId = samples.map { it.copy(brewId = brewId) }
        // Steg 3: Lägg till mätpunkterna
        insertBrewSamples(samplesWithBrewId)
        // Steg 4: Minska lagersaldot
        decrementBeanStock(brew.beanId, brew.doseGrams)
        // Steg 5: Uppdatera bönan manuellt för att trigga Flow-uppdatering
        val bean = getBeanById(brew.beanId)
        if (bean != null) updateBean(bean)

        return brewId
    }

    /**
     * Transaktion för att lägga till en enkel bryggning (utan mätpunkter).
     * Minskar lagersaldot och triggar Flow-uppdatering.
     */
    @Transaction
    suspend fun addBrew(brew: Brew): Long {
        // Steg 1: Lägg till bryggningen
        val brewId = insertBrew(brew)
        // Steg 2: Minska lagersaldot
        decrementBeanStock(brew.beanId, brew.doseGrams)
        // Steg 3: Uppdatera bönan manuellt för att trigga Flow-uppdatering
        val bean = getBeanById(brew.beanId)
        if (bean != null) updateBean(bean)

        return brewId
    }
}