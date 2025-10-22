package com.victorkoffed.projektandroid.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) för kaffedatabasen.
 * Defines all SQL queries and operations for the database.
 * This version includes all necessary CRUD functions.
 */
@Dao
interface CoffeeDao {

    // --- Grinder ---
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGrinder(grinder: Grinder)

    @Update
    suspend fun updateGrinder(grinder: Grinder)

    @Delete
    suspend fun deleteGrinder(grinder: Grinder)

    @Query("SELECT * FROM Grinder ORDER BY name ASC")
    fun getAllGrinders(): Flow<List<Grinder>>

    // --- Method ---
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMethod(method: Method)

    @Update
    suspend fun updateMethod(method: Method) // <-- NY

    @Delete
    suspend fun deleteMethod(method: Method) // <-- NY

    @Query("SELECT * FROM Method ORDER BY name ASC")
    fun getAllMethods(): Flow<List<Method>>

    // --- Bean ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBean(bean: Bean)

    @Update
    suspend fun updateBean(bean: Bean)

    @Delete
    suspend fun deleteBean(bean: Bean)

    @Query("SELECT * FROM Bean ORDER BY name ASC")
    fun getAllBeans(): Flow<List<Bean>>

    @Query("SELECT * FROM Bean WHERE bean_id = :id")
    suspend fun getBeanById(id: Long): Bean?

    // --- Brew ---
    @Insert
    suspend fun insertBrew(brew: Brew): Long // Returns ID for linking samples

    @Update
    suspend fun updateBrew(brew: Brew) // <-- NY

    @Delete
    suspend fun deleteBrew(brew: Brew) // <-- NY

    @Query("SELECT * FROM Brew WHERE brew_id = :id")
    suspend fun getBrewById(id: Long): Brew? // <-- NY

    @Query("SELECT * FROM Brew ORDER BY started_at DESC")
    fun getAllBrews(): Flow<List<Brew>> // <-- NY

    // --- BrewSample ---
    @Insert
    suspend fun insertBrewSamples(samples: List<BrewSample>)

    @Query("SELECT * FROM BrewSample WHERE brew_id = :brewId ORDER BY t_ms ASC")
    fun getSamplesForBrew(brewId: Long): Flow<List<BrewSample>> // <-- NY

    // --- BrewMetrics (View) ---
    @Query("SELECT * FROM BrewMetrics WHERE brew_id = :brewId")
    fun getBrewMetrics(brewId: Long): Flow<BrewMetrics?>

    // --- Transactional Function ---
    /**
     * Saves a new brew and its samples atomically.
     * Returns the new Brew ID.
     */
    @Transaction
    suspend fun addBrewWithSamples(brew: Brew, samples: List<BrewSample>): Long {
        val brewId = insertBrew(brew)
        val samplesWithBrewId = samples.map { it.copy(brewId = brewId) }
        insertBrewSamples(samplesWithBrewId)
        return brewId
    }
}

