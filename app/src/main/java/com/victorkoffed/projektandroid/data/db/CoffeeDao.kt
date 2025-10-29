package com.victorkoffed.projektandroid.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CoffeeDao {
    // --- Grinder (Kvarn) ---
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertGrinder(grinder: Grinder)
    @Update suspend fun updateGrinder(grinder: Grinder)
    @Delete suspend fun deleteGrinder(grinder: Grinder)
    @Query("SELECT * FROM Grinder ORDER BY name ASC") fun getAllGrinders(): Flow<List<Grinder>>
    @Query("SELECT * FROM Grinder WHERE grinder_id = :id") suspend fun getGrinderById(id: Long): Grinder?

    // --- Method (Bryggmetod) ---
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertMethod(method: Method)
    @Update suspend fun updateMethod(method: Method)
    @Delete suspend fun deleteMethod(method: Method)
    @Query("SELECT * FROM Method ORDER BY name ASC") fun getAllMethods(): Flow<List<Method>>
    @Query("SELECT * FROM Method WHERE method_id = :id") suspend fun getMethodById(id: Long): Method?

    // --- Bean (Kaffeböna) ---
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertBean(bean: Bean)
    @Update suspend fun updateBean(bean: Bean)
    @Delete suspend fun deleteBean(bean: Bean)
    @Query("SELECT * FROM Bean WHERE is_archived = 0 ORDER BY name ASC") fun getAllBeans(): Flow<List<Bean>>
    @Query("SELECT * FROM Bean WHERE is_archived = 1 ORDER BY name ASC") fun getArchivedBeans(): Flow<List<Bean>>
    @Query("SELECT * FROM Bean WHERE bean_id = :id") suspend fun getBeanById(id: Long): Bean?
    @Query("SELECT * FROM Bean WHERE bean_id = :id") fun observeBean(id: Long): Flow<Bean?>
    @Query("UPDATE Bean SET is_archived = :isArchived WHERE bean_id = :id")
    suspend fun updateBeanArchivedStatus(id: Long, isArchived: Boolean)
    @Query("SELECT COUNT(*) FROM Bean") fun getTotalBeanCount(): Flow<Int>
    @Query("UPDATE Bean SET remaining_weight_g = remaining_weight_g + :dose WHERE bean_id = :beanId")
    suspend fun incrementBeanStock(beanId: Long, dose: Double)
    @Query("UPDATE Bean SET remaining_weight_g = MAX(0, remaining_weight_g - :dose) WHERE bean_id = :beanId")
    suspend fun decrementBeanStock(beanId: Long, dose: Double)

    // --- Brew (Bryggning) ---
    @Insert suspend fun insertBrew(brew: Brew): Long
    @Update suspend fun updateBrew(brew: Brew)
    @Delete suspend fun deleteBrew(brew: Brew)
    @Query("SELECT * FROM Brew WHERE brew_id = :id") suspend fun getBrewById(id: Long): Brew?
    @Query("""
        SELECT B.*
        FROM Brew B
        JOIN Bean A ON B.bean_id = A.bean_id
        WHERE A.is_archived = 0
        ORDER BY B.started_at DESC
    """)
    fun getAllBrews(): Flow<List<Brew>>

    // NY FUNKTION: Hämta ALLA brews, oavsett bönans arkivstatus, sorterade efter datum
    @Query("SELECT * FROM Brew ORDER BY started_at DESC")
    fun getAllBrewsIncludingArchived(): Flow<List<Brew>>

    @Query("SELECT * FROM Brew WHERE brew_id = :id") fun observeBrew(id: Long): Flow<Brew?>

    // Räkna ALLA bryggningar
    @Query("SELECT COUNT(*) FROM Brew")
    fun getTotalBrewCount(): Flow<Int> // Returnerar ett Flow med totala antalet

    @Transaction
    suspend fun deleteBrewTransaction(brew: Brew) {
        incrementBeanStock(brew.beanId, brew.doseGrams)
        deleteBrew(brew)
    }

    @Query("SELECT * FROM Brew WHERE bean_id = :beanId ORDER BY started_at DESC")
    fun getBrewsForBean(beanId: Long): Flow<List<Brew>>

    // --- BrewSample (Bryggdata-punkter) ---
    @Insert suspend fun insertBrewSamples(samples: List<BrewSample>)
    @Query("SELECT * FROM BrewSample WHERE brew_id = :brewId ORDER BY t_ms ASC")
    fun getSamplesForBrew(brewId: Long): Flow<List<BrewSample>>

    // --- BrewMetrics (View: Beräknade Mått) ---
    @Query("SELECT * FROM BrewMetrics WHERE brew_id = :brewId")
    fun getBrewMetrics(brewId: Long): Flow<BrewMetrics?>

    // --- Transaktionella Hjälpfunktioner ---
    @Transaction
    suspend fun addBrewWithSamples(brew: Brew, samples: List<BrewSample>): Long {
        val brewId = insertBrew(brew)
        val samplesWithBrewId = samples.map { it.copy(brewId = brewId) }
        insertBrewSamples(samplesWithBrewId)
        decrementBeanStock(brew.beanId, brew.doseGrams)
        return brewId
    }

    @Transaction
    suspend fun addBrew(brew: Brew): Long {
        val brewId = insertBrew(brew)
        decrementBeanStock(brew.beanId, brew.doseGrams)
        return brewId
    }
}