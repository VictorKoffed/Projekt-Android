package com.victorkoffed.projektandroid.data.repository.interfaces

import com.victorkoffed.projektandroid.data.db.Brew
import com.victorkoffed.projektandroid.data.db.BrewMetrics
import com.victorkoffed.projektandroid.data.db.BrewSample
import kotlinx.coroutines.flow.Flow

/**
 * Defines the data operations for coffee brewing sessions, high-frequency telemetry samples,
 * and derived metrics. Abstracts the underlying persistence layer and enforces business rules
 * such as inventory synchronization during brew lifecycle events.
 */
interface BrewRepository {
    /**
     * Retrieves active brews, exclusively filtering out any records associated with archived beans.
     */
    fun getAllBrews(): Flow<List<Brew>>

    fun getAllBrewsIncludingArchived(): Flow<List<Brew>>
    suspend fun getBrewById(id: Long): Brew?
    suspend fun addBrew(brew: Brew): Long
    suspend fun updateBrew(brew: Brew)
    suspend fun deleteBrew(brew: Brew)

    /**
     * Orchestrates a transactional deletion of a brew while automatically reverting
     * the associated bean's inventory deduction.
     */
    suspend fun deleteBrewAndRestoreStock(brew: Brew)

    /**
     * Atomically persists a brew session alongside its real-time scale measurements.
     */
    suspend fun addBrewWithSamples(brew: Brew, samples: List<BrewSample>): Long

    fun observeBrew(brewId: Long): Flow<Brew?>
    fun getBrewsForBean(beanId: Long): Flow<List<Brew>>
    fun getTotalBrewCount(): Flow<Int>

    fun getSamplesForBrew(brewId: Long): Flow<List<BrewSample>>
    suspend fun addBrewSamples(samples: List<BrewSample>)

    fun getBrewMetrics(brewId: Long): Flow<BrewMetrics?>
}