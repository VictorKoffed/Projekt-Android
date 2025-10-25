package com.victorkoffed.projektandroid.data.repository

import com.victorkoffed.projektandroid.data.db.*
import kotlinx.coroutines.flow.Flow

class CoffeeRepositoryImpl(private val dao: CoffeeDao) : CoffeeRepository {
    // --- Grinder ---
    override fun getAllGrinders(): Flow<List<Grinder>> = dao.getAllGrinders()
    override suspend fun addGrinder(grinder: Grinder) = dao.insertGrinder(grinder)
    override suspend fun updateGrinder(grinder: Grinder) = dao.updateGrinder(grinder)
    override suspend fun deleteGrinder(grinder: Grinder) = dao.deleteGrinder(grinder)
    override suspend fun getGrinderById(id: Long): Grinder? = dao.getGrinderById(id)

    // --- Method ---
    override fun getAllMethods(): Flow<List<Method>> = dao.getAllMethods()
    override suspend fun addMethod(method: Method) = dao.insertMethod(method)
    override suspend fun updateMethod(method: Method) = dao.updateMethod(method)
    override suspend fun deleteMethod(method: Method) = dao.deleteMethod(method)
    override suspend fun getMethodById(id: Long): Method? = dao.getMethodById(id)

    // --- Bean ---
    override fun getAllBeans(): Flow<List<Bean>> = dao.getAllBeans()
    override suspend fun addBean(bean: Bean) = dao.insertBean(bean)
    override suspend fun updateBean(bean: Bean) = dao.updateBean(bean)
    override suspend fun getBeanById(id: Long): Bean? = dao.getBeanById(id)
    override suspend fun deleteBean(bean: Bean) = dao.deleteBean(bean)

    // --- Brew ---
    override fun getAllBrews(): Flow<List<Brew>> = dao.getAllBrews()
    override suspend fun getBrewById(id: Long): Brew? = dao.getBrewById(id)
    override suspend fun addBrew(brew: Brew): Long = dao.addBrew(brew)
    override suspend fun updateBrew(brew: Brew) = dao.updateBrew(brew)
    override suspend fun deleteBrew(brew: Brew) = dao.deleteBrew(brew)
    override suspend fun deleteBrewAndRestoreStock(brew: Brew) = dao.deleteBrewTransaction(brew) // NY IMPLEMENTATION
    override suspend fun addBrewWithSamples(brew: Brew, samples: List<BrewSample>): Long = dao.addBrewWithSamples(brew, samples)

    // --- NY IMPLEMENTATION ---
    override fun getBrewsForBean(beanId: Long): Flow<List<Brew>> = dao.getBrewsForBean(beanId)

    // --- BrewSample ---
    override fun getSamplesForBrew(brewId: Long): Flow<List<BrewSample>> = dao.getSamplesForBrew(brewId)
    override suspend fun addBrewSamples(samples: List<BrewSample>) = dao.insertBrewSamples(samples)

    // --- BrewMetrics (View) ---
    override fun getBrewMetrics(brewId: Long): Flow<BrewMetrics?> = dao.getBrewMetrics(brewId)
}