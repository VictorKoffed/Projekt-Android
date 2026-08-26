package com.victorkoffed.projektandroid.data.repository.interfaces

import com.victorkoffed.projektandroid.data.db.Method
import kotlinx.coroutines.flow.Flow

/**
 * Defines the data operations for managing brewing methods (e.g., V60, Aeropress).
 * Abstracts the persistence layer to allow observation and management of
 * method configurations used to categorize brew sessions.
 */
interface MethodRepository {
    fun getAllMethods(): Flow<List<Method>>
    suspend fun addMethod(method: Method)
    suspend fun updateMethod(method: Method)
    suspend fun deleteMethod(method: Method)
    suspend fun getMethodById(id: Long): Method?
}