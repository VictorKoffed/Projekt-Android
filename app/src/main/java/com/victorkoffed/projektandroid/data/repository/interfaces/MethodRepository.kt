package com.victorkoffed.projektandroid.data.repository.interfaces

import com.victorkoffed.projektandroid.data.db.Method
import kotlinx.coroutines.flow.Flow

/**
 * Interface för att hantera dataoperationer relaterade till Bryggmetoder (Method).
 */
interface MethodRepository {
    fun getAllMethods(): Flow<List<Method>>
    suspend fun addMethod(method: Method)
    suspend fun updateMethod(method: Method)
    suspend fun deleteMethod(method: Method)
    suspend fun getMethodById(id: Long): Method?
}