package com.victorkoffed.projektandroid.data.repository

import com.victorkoffed.projektandroid.data.db.Bean
import com.victorkoffed.projektandroid.data.db.Brew
import com.victorkoffed.projektandroid.data.db.BrewMetrics
import com.victorkoffed.projektandroid.data.db.BrewSample
import com.victorkoffed.projektandroid.data.db.Grinder
import com.victorkoffed.projektandroid.data.db.Method
import kotlinx.coroutines.flow.Flow

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
    fun getArchivedBeans(): Flow<List<Bean>>
    suspend fun addBean(bean: Bean)
    suspend fun updateBean(bean: Bean)
    suspend fun getBeanById(id: Long): Bean?
    fun observeBean(beanId: Long): Flow<Bean?>
    suspend fun deleteBean(bean: Bean)
    suspend fun updateBeanArchivedStatus(id: Long, isArchived: Boolean)
    fun getTotalBeanCount(): Flow<Int>

    // --- Bryggning (Brew) ---
    fun getAllBrews(): Flow<List<Brew>> // Hämtar fortfarande bara aktiva för listan
    fun getAllBrewsIncludingArchived(): Flow<List<Brew>> // NY FUNKTION
    suspend fun getBrewById(id: Long): Brew?
    suspend fun addBrew(brew: Brew): Long
    suspend fun updateBrew(brew: Brew)
    suspend fun deleteBrew(brew: Brew)
    suspend fun deleteBrewAndRestoreStock(brew: Brew)
    suspend fun addBrewWithSamples(brew: Brew, samples: List<BrewSample>): Long
    fun observeBrew(brewId: Long): Flow<Brew?>
    fun getBrewsForBean(beanId: Long): Flow<List<Brew>>
    fun getTotalBrewCount(): Flow<Int> // Hämtar totalt antal bryggningar

    // --- Mätpunkter (BrewSample) ---
    fun getSamplesForBrew(brewId: Long): Flow<List<BrewSample>>
    suspend fun addBrewSamples(samples: List<BrewSample>)

    // --- Beräknade Mått (BrewMetrics) ---
    fun getBrewMetrics(brewId: Long): Flow<BrewMetrics?>
}