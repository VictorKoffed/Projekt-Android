package com.victorkoffed.projektandroid.data.repository

import com.victorkoffed.projektandroid.data.db.*
import kotlinx.coroutines.flow.Flow

/**
 * Implementation av CoffeeRepository som använder Room (CoffeeDao).
 * Denna version inkluderar alla CRUD-funktioner från interfacet.
 */
class CoffeeRepositoryImpl(private val dao: CoffeeDao) : CoffeeRepository {

    // --- Grinder ---
    override fun getAllGrinders(): Flow<List<Grinder>> = dao.getAllGrinders()
    override suspend fun addGrinder(grinder: Grinder) = dao.insertGrinder(grinder)
    override suspend fun updateGrinder(grinder: Grinder) = dao.updateGrinder(grinder) // Anropar (snart) DAO
    override suspend fun deleteGrinder(grinder: Grinder) = dao.deleteGrinder(grinder)

    // --- Method ---
    override fun getAllMethods(): Flow<List<Method>> = dao.getAllMethods()
    override suspend fun addMethod(method: Method) = dao.insertMethod(method)
    override suspend fun updateMethod(method: Method) = dao.updateMethod(method) // Anropar (snart) DAO
    override suspend fun deleteMethod(method: Method) = dao.deleteMethod(method) // Anropar (snart) DAO

    // --- Bean ---
    override fun getAllBeans(): Flow<List<Bean>> = dao.getAllBeans()
    override suspend fun addBean(bean: Bean) = dao.insertBean(bean)
    override suspend fun updateBean(bean: Bean) = dao.updateBean(bean)
    override suspend fun getBeanById(id: Long): Bean? = dao.getBeanById(id)
    override suspend fun deleteBean(bean: Bean) = dao.deleteBean(bean)

    // --- Brew ---
    override fun getAllBrews(): Flow<List<Brew>> = dao.getAllBrews() // Anropar (snart) DAO
    override suspend fun getBrewById(id: Long): Brew? = dao.getBrewById(id) // Anropar (snart) DAO
    override suspend fun addBrew(brew: Brew): Long = dao.insertBrew(brew)
    override suspend fun updateBrew(brew: Brew) = dao.updateBrew(brew) // Anropar (snart) DAO
    override suspend fun deleteBrew(brew: Brew) = dao.deleteBrew(brew) // Anropar (snart) DAO
    override suspend fun addBrewWithSamples(brew: Brew, samples: List<BrewSample>): Long =
        dao.addBrewWithSamples(brew, samples)

    // --- BrewSample ---
    override fun getSamplesForBrew(brewId: Long): Flow<List<BrewSample>> = dao.getSamplesForBrew(brewId) // Anropar (snart) DAO
    override suspend fun addBrewSamples(samples: List<BrewSample>) = dao.insertBrewSamples(samples)

    // --- BrewMetrics (View) ---
    override fun getBrewMetrics(brewId: Long): Flow<BrewMetrics?> = dao.getBrewMetrics(brewId)
}

