package com.victorkoffed.projektandroid.data.repository.interfaces

import com.victorkoffed.projektandroid.data.db.Grinder
import kotlinx.coroutines.flow.Flow

/**
 * Interface för att hantera dataoperationer relaterade till Kvarnar (Grinder).
 */
interface GrinderRepository {
    fun getAllGrinders(): Flow<List<Grinder>>
    suspend fun addGrinder(grinder: Grinder)
    suspend fun updateGrinder(grinder: Grinder)
    suspend fun deleteGrinder(grinder: Grinder)
    suspend fun getGrinderById(id: Long): Grinder?
}