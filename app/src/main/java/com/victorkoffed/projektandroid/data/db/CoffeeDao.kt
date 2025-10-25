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
    // --- Grinder ---
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertGrinder(grinder: Grinder)
    @Update suspend fun updateGrinder(grinder: Grinder)
    @Delete suspend fun deleteGrinder(grinder: Grinder)
    @Query("SELECT * FROM Grinder ORDER BY name ASC") fun getAllGrinders(): Flow<List<Grinder>>
    @Query("SELECT * FROM Grinder WHERE grinder_id = :id") suspend fun getGrinderById(id: Long): Grinder?

    // --- Method ---
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertMethod(method: Method)
    @Update suspend fun updateMethod(method: Method)
    @Delete suspend fun deleteMethod(method: Method)
    @Query("SELECT * FROM Method ORDER BY name ASC") fun getAllMethods(): Flow<List<Method>>
    @Query("SELECT * FROM Method WHERE method_id = :id") suspend fun getMethodById(id: Long): Method?

    // --- Bean ---
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertBean(bean: Bean)
    @Update suspend fun updateBean(bean: Bean)
    @Delete suspend fun deleteBean(bean: Bean)
    @Query("SELECT * FROM Bean ORDER BY name ASC") fun getAllBeans(): Flow<List<Bean>>
    @Query("SELECT * FROM Bean WHERE bean_id = :id") suspend fun getBeanById(id: Long): Bean?
    // Hjälpfunktioner för lager
    @Query("UPDATE Bean SET remaining_weight_g = remaining_weight_g + :dose WHERE bean_id = :beanId")
    suspend fun incrementBeanStock(beanId: Long, dose: Double)
    @Query("UPDATE Bean SET remaining_weight_g = MAX(0, remaining_weight_g - :dose) WHERE bean_id = :beanId")
    suspend fun decrementBeanStock(beanId: Long, dose: Double)

    // --- Brew ---
    @Insert suspend fun insertBrew(brew: Brew): Long
    @Update suspend fun updateBrew(brew: Brew)
    @Delete suspend fun deleteBrew(brew: Brew)
    @Query("SELECT * FROM Brew WHERE brew_id = :id") suspend fun getBrewById(id: Long): Brew?
    @Query("SELECT * FROM Brew ORDER BY started_at DESC") fun getAllBrews(): Flow<List<Brew>>

    // ✅ NYTT: Observera en enskild bryggning reaktivt
    @Query("SELECT * FROM Brew WHERE brew_id = :id")
    fun observeBrew(id: Long): Flow<Brew?>

    // --- Brew deletion with stock restore (NEW TRANSACTION) ---
    @Transaction
    suspend fun deleteBrewTransaction(brew: Brew) {
        // Step 1: Restore stock BEFORE deletion
        incrementBeanStock(brew.beanId, brew.doseGrams)
        // Step 2: Delete the brew (CASCADE deletes Samples)
        deleteBrew(brew)
        // Step 3: Force Flow update of the bean
        val bean = getBeanById(brew.beanId)
        if (bean != null) updateBean(bean)
    }

    // --- NEW QUERY ---
    @Query("SELECT * FROM Brew WHERE bean_id = :beanId ORDER BY started_at DESC")
    fun getBrewsForBean(beanId: Long): Flow<List<Brew>>

    // --- BrewSample ---
    @Insert suspend fun insertBrewSamples(samples: List<BrewSample>)
    @Query("SELECT * FROM BrewSample WHERE brew_id = :brewId ORDER BY t_ms ASC")
    fun getSamplesForBrew(brewId: Long): Flow<List<BrewSample>>

    // --- BrewMetrics (View) ---
    @Query("SELECT * FROM BrewMetrics WHERE brew_id = :brewId")
    fun getBrewMetrics(brewId: Long): Flow<BrewMetrics?>

    // --- Transactional Function for Brew with Samples (MODIFIERAD) ---
    @Transaction
    suspend fun addBrewWithSamples(brew: Brew, samples: List<BrewSample>): Long {
        val brewId = insertBrew(brew)
        val samplesWithBrewId = samples.map { it.copy(brewId = brewId) }
        insertBrewSamples(samplesWithBrewId)
        decrementBeanStock(brew.beanId, brew.doseGrams)
        val bean = getBeanById(brew.beanId)
        if (bean != null) updateBean(bean)
        return brewId
    }

    // --- Transactional Function for simple Brew (MODIFIERAD) ---
    @Transaction
    suspend fun addBrew(brew: Brew): Long {
        val brewId = insertBrew(brew)
        decrementBeanStock(brew.beanId, brew.doseGrams)
        val bean = getBeanById(brew.beanId)
        if (bean != null) updateBean(bean)
        return brewId
    }
}