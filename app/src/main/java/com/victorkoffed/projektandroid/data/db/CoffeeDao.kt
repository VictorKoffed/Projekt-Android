package com.victorkoffed.projektandroid.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) för kaffedatabasen.
 * Definierar alla SQL-frågor och operationer som kan göras mot databasen.
 */
@Dao
interface CoffeeDao {

    // --- Grinder ---
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGrinder(grinder: Grinder) // Ska inte returnera Long om OnConflictStrategy.IGNORE används

    @Delete
    suspend fun deleteGrinder(grinder: Grinder)

    @Query("SELECT * FROM Grinder ORDER BY name ASC")
    fun getAllGrinders(): Flow<List<Grinder>>

    // --- Method ---
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMethod(method: Method) // Ska inte returnera Long om OnConflictStrategy.IGNORE används

    @Query("SELECT * FROM Method ORDER BY name ASC")
    fun getAllMethods(): Flow<List<Method>>

    // --- Bean ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBean(bean: Bean) // Ska inte returnera Long om OnConflictStrategy.REPLACE används

    @Update
    suspend fun updateBean(bean: Bean)

    @Query("SELECT * FROM Bean ORDER BY name ASC")
    fun getAllBeans(): Flow<List<Bean>> // Funktion som saknades

    @Query("SELECT * FROM Bean WHERE bean_id = :id")
    suspend fun getBeanById(id: Long): Bean? // Funktion som saknades, använder Long

    // --- Brew ---
    @Insert
    suspend fun insertBrew(brew: Brew): Long // Returnerar det nya brew_id

    @Query("SELECT * FROM Brew WHERE brew_id = :id")
    suspend fun getBrewById(id: Long): Brew?

    @Query("SELECT * FROM Brew ORDER BY started_at DESC") // Korrigerat kolumnnamn
    fun getAllBrews(): Flow<List<Brew>>

    // --- BrewSample ---
    @Insert
    suspend fun insertBrewSamples(samples: List<BrewSample>)

    @Query("SELECT * FROM BrewSample WHERE brew_id = :brewId ORDER BY t_ms ASC")
    fun getSamplesForBrew(brewId: Long): Flow<List<BrewSample>>

    // --- BrewMetrics (View) ---
    @Query("SELECT * FROM BrewMetrics WHERE brew_id = :brewId")
    fun getBrewMetrics(brewId: Long): Flow<BrewMetrics?> // Funktion som saknades, returnerar Nullable
}

