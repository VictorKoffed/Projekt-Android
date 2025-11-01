package com.victorkoffed.projektandroid.data.repository.interfaces

import com.victorkoffed.projektandroid.data.db.Brew
import com.victorkoffed.projektandroid.data.db.BrewMetrics
import com.victorkoffed.projektandroid.data.db.BrewSample
import kotlinx.coroutines.flow.Flow

/**
 * Interface för att hantera dataoperationer relaterade till Bryggningar (Brew) och Mätpunkter (BrewSample).
 */
interface BrewRepository {
    fun getAllBrews(): Flow<List<Brew>> // Hämtar bara aktiva för listan
    fun getAllBrewsIncludingArchived(): Flow<List<Brew>>
    suspend fun getBrewById(id: Long): Brew?
    suspend fun addBrew(brew: Brew): Long
    suspend fun updateBrew(brew: Brew)
    suspend fun deleteBrew(brew: Brew)
    suspend fun deleteBrewAndRestoreStock(brew: Brew)
    suspend fun addBrewWithSamples(brew: Brew, samples: List<BrewSample>): Long
    fun observeBrew(brewId: Long): Flow<Brew?>
    fun getBrewsForBean(beanId: Long): Flow<List<Brew>>
    fun getTotalBrewCount(): Flow<Int>

    // --- Mätpunkter (BrewSample) ---
    fun getSamplesForBrew(brewId: Long): Flow<List<BrewSample>>
    suspend fun addBrewSamples(samples: List<BrewSample>)

    // --- Beräknade Mått (BrewMetrics) ---
    fun getBrewMetrics(brewId: Long): Flow<BrewMetrics?>
}