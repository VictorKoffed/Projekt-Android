package com.victorkoffed.projektandroid.data.repository

import com.victorkoffed.projektandroid.data.db.Bean
import com.victorkoffed.projektandroid.data.db.Brew
import com.victorkoffed.projektandroid.data.db.BrewMetrics
import com.victorkoffed.projektandroid.data.db.BrewSample
import com.victorkoffed.projektandroid.data.db.Grinder
import com.victorkoffed.projektandroid.data.db.Method
import kotlinx.coroutines.flow.Flow

/**
 * Kontraktet (interfacet) för att hantera all kafferelaterad data.
 * Alla CRUD-operationer mot databasen ska gå via denna repository.
 */
interface CoffeeRepository {
    // --- Kvarn (Grinder) ---
    fun getAllGrinders(): Flow<List<Grinder>>
    suspend fun addGrinder(grinder: Grinder)
    suspend fun updateGrinder(grinder: Grinder)
    suspend fun deleteGrinder(grinder: Grinder)
    suspend fun getGrinderById(id: Long): Grinder?

    // --- Metod (Method) ---
    fun getAllMethods(): Flow<List<Method>>
    suspend fun addMethod(method: Method)
    suspend fun updateMethod(method: Method)
    suspend fun deleteMethod(method: Method)
    suspend fun getMethodById(id: Long): Method?

    // --- Böna (Bean) ---
    fun getAllBeans(): Flow<List<Bean>>
    suspend fun addBean(bean: Bean)
    suspend fun updateBean(bean: Bean)
    suspend fun getBeanById(id: Long): Bean?
    suspend fun deleteBean(bean: Bean)

    // --- Bryggning (Brew) ---
    fun getAllBrews(): Flow<List<Brew>>
    suspend fun getBrewById(id: Long): Brew?
    /** Lägger till en bryggning och returnerar dess ID, minskar bönlagret. */
    suspend fun addBrew(brew: Brew): Long
    suspend fun updateBrew(brew: Brew)
    suspend fun deleteBrew(brew: Brew)
    /** Utför en transaktionell radering av bryggningen och återställer bönans lagersaldo. */
    suspend fun deleteBrewAndRestoreStock(brew: Brew)
    /** Lägger till en bryggning och dess associerade mätpunkter (Samples) i en transaktion, minskar lagret. */
    suspend fun addBrewWithSamples(brew: Brew, samples: List<BrewSample>): Long

    /** Reaktiv observation av en enskild bryggning. Användbart för att visa detaljer. */
    fun observeBrew(brewId: Long): Flow<Brew?>

    /** Hämtar alla bryggningar som gjorts med en specifik böna. */
    fun getBrewsForBean(beanId: Long): Flow<List<Brew>>

    // --- Mätpunkter (BrewSample) ---
    /** Hämtar tidsseriedata (mätpunkter) för en specifik bryggning. */
    fun getSamplesForBrew(brewId: Long): Flow<List<BrewSample>>
    suspend fun addBrewSamples(samples: List<BrewSample>)

    // --- Beräknade Mått (BrewMetrics) ---
    /** Hämtar de beräknade nyckeltalen (t.ex. ratio, vattenmängd) för en bryggning. */
    fun getBrewMetrics(brewId: Long): Flow<BrewMetrics?>
}