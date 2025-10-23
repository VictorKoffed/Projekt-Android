package com.victorkoffed.projektandroid.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CoffeeDao {
    // --- Grinder ---
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertGrinder(grinder: Grinder)
    @Update suspend fun updateGrinder(grinder: Grinder)
    @Delete suspend fun deleteGrinder(grinder: Grinder)
    @Query("SELECT * FROM Grinder ORDER BY name ASC") fun getAllGrinders(): Flow<List<Grinder>>
    @Query("SELECT * FROM Grinder WHERE grinder_id = :id") suspend fun getGrinderById(id: Long): Grinder? // <-- NY RAD

    // --- Method ---
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertMethod(method: Method)
    @Update suspend fun updateMethod(method: Method)
    @Delete suspend fun deleteMethod(method: Method)
    @Query("SELECT * FROM Method ORDER BY name ASC") fun getAllMethods(): Flow<List<Method>>
    @Query("SELECT * FROM Method WHERE method_id = :id") suspend fun getMethodById(id: Long): Method? // <-- NY RAD

    // --- Bean ---
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertBean(bean: Bean)
    @Update suspend fun updateBean(bean: Bean)
    @Delete suspend fun deleteBean(bean: Bean)
    @Query("SELECT * FROM Bean ORDER BY name ASC") fun getAllBeans(): Flow<List<Bean>>
    @Query("SELECT * FROM Bean WHERE bean_id = :id") suspend fun getBeanById(id: Long): Bean?

    // --- Brew ---
    @Insert suspend fun insertBrew(brew: Brew): Long
    @Update suspend fun updateBrew(brew: Brew)
    @Delete suspend fun deleteBrew(brew: Brew)
    @Query("SELECT * FROM Brew WHERE brew_id = :id") suspend fun getBrewById(id: Long): Brew?
    @Query("SELECT * FROM Brew ORDER BY started_at DESC") fun getAllBrews(): Flow<List<Brew>>

    // --- BrewSample ---
    @Insert suspend fun insertBrewSamples(samples: List<BrewSample>)
    @Query("SELECT * FROM BrewSample WHERE brew_id = :brewId ORDER BY t_ms ASC") fun getSamplesForBrew(brewId: Long): Flow<List<BrewSample>>

    // --- BrewMetrics (View) ---
    @Query("SELECT * FROM BrewMetrics WHERE brew_id = :brewId") fun getBrewMetrics(brewId: Long): Flow<BrewMetrics?>

    // --- Transactional Function ---
    @Transaction suspend fun addBrewWithSamples(brew: Brew, samples: List<BrewSample>): Long {
        val brewId = insertBrew(brew)
        val samplesWithBrewId = samples.map { it.copy(brewId = brewId) }
        insertBrewSamples(samplesWithBrewId)
        return brewId
    }
}

