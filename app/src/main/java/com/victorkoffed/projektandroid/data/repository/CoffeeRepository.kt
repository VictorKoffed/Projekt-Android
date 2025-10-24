package com.victorkoffed.projektandroid.data.repository

import com.victorkoffed.projektandroid.data.db.*
import kotlinx.coroutines.flow.Flow

interface CoffeeRepository {
    // --- Grinder ---
    fun getAllGrinders(): Flow<List<Grinder>>
    suspend fun addGrinder(grinder: Grinder)
    suspend fun updateGrinder(grinder: Grinder)
    suspend fun deleteGrinder(grinder: Grinder)
    suspend fun getGrinderById(id: Long): Grinder?

    // --- Method ---
    fun getAllMethods(): Flow<List<Method>>
    suspend fun addMethod(method: Method)
    suspend fun updateMethod(method: Method)
    suspend fun deleteMethod(method: Method)
    suspend fun getMethodById(id: Long): Method?

    // --- Bean ---
    fun getAllBeans(): Flow<List<Bean>>
    suspend fun addBean(bean: Bean)
    suspend fun updateBean(bean: Bean)
    suspend fun getBeanById(id: Long): Bean?
    suspend fun deleteBean(bean: Bean)

    // --- Brew ---
    fun getAllBrews(): Flow<List<Brew>>
    suspend fun getBrewById(id: Long): Brew?
    suspend fun addBrew(brew: Brew): Long
    suspend fun updateBrew(brew: Brew)
    suspend fun deleteBrew(brew: Brew)
    suspend fun addBrewWithSamples(brew: Brew, samples: List<BrewSample>): Long

    // --- NY FUNKTION ---
    fun getBrewsForBean(beanId: Long): Flow<List<Brew>>
    // --- SLUT NY FUNKTION ---

    // --- BrewSample ---
    fun getSamplesForBrew(brewId: Long): Flow<List<BrewSample>>
    suspend fun addBrewSamples(samples: List<BrewSample>)

    // --- BrewMetrics (View) ---
    fun getBrewMetrics(brewId: Long): Flow<BrewMetrics?>
}