package com.victorkoffed.projektandroid.data.repository

import com.victorkoffed.projektandroid.data.db.*
import kotlinx.coroutines.flow.Flow

interface CoffeeRepository {
    // --- Grinder ---
    fun getAllGrinders(): Flow<List<Grinder>>
    suspend fun addGrinder(grinder: Grinder)
    suspend fun deleteGrinder(grinder: Grinder)

    // --- Method ---
    fun getAllMethods(): Flow<List<Method>>
    suspend fun addMethod(method: Method)

    // --- Bean ---
    fun getAllBeans(): Flow<List<Bean>>
    suspend fun addBean(bean: Bean)
    suspend fun updateBean(bean: Bean)
    suspend fun getBeanById(id: Long): Bean?

    // --- Brew ---
    suspend fun addBrew(brew: Brew): Long
    suspend fun addBrewSamples(samples: List<BrewSample>)
    fun getBrewMetrics(brewId: Long): Flow<BrewMetrics?>
}
