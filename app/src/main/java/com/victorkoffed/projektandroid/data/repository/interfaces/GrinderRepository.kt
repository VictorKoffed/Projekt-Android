package com.victorkoffed.projektandroid.data.repository.interfaces

import com.victorkoffed.projektandroid.data.db.Grinder
import kotlinx.coroutines.flow.Flow

/**
 * Defines the data operations for managing coffee grinder equipment profiles.
 * Abstracts the persistence layer to allow observation and management of
 * grinder configurations used across brewing sessions.
 */
interface GrinderRepository {
    fun getAllGrinders(): Flow<List<Grinder>>
    suspend fun addGrinder(grinder: Grinder)
    suspend fun updateGrinder(grinder: Grinder)
    suspend fun deleteGrinder(grinder: Grinder)
    suspend fun getGrinderById(id: Long): Grinder?
}