package com.victorkoffed.projektandroid.data.repository

import com.victorkoffed.projektandroid.data.db.Bean
import com.victorkoffed.projektandroid.data.db.Brew
import com.victorkoffed.projektandroid.data.db.BrewMetrics
import com.victorkoffed.projektandroid.data.db.BrewSample
import com.victorkoffed.projektandroid.data.db.CoffeeDao
import com.victorkoffed.projektandroid.data.db.Grinder
import com.victorkoffed.projektandroid.data.db.Method
import kotlinx.coroutines.flow.Flow

/**
 * Implementation av CoffeeRepository som använder Room (CoffeeDao).
 */
class CoffeeRepositoryImpl(private val dao: CoffeeDao) : CoffeeRepository {

    // --- Grinder ---
    override fun getAllGrinders(): Flow<List<Grinder>> = dao.getAllGrinders()
    override suspend fun addGrinder(grinder: Grinder) = dao.insertGrinder(grinder) // Korrekt funktion från DAO
    override suspend fun deleteGrinder(grinder: Grinder) = dao.deleteGrinder(grinder)

    // --- Method ---
    override fun getAllMethods(): Flow<List<Method>> = dao.getAllMethods()
    override suspend fun addMethod(method: Method) = dao.insertMethod(method) // Korrekt funktion från DAO

    // --- Bean ---
    override fun getAllBeans(): Flow<List<Bean>> = dao.getAllBeans() // Korrekt funktion från DAO
    override suspend fun addBean(bean: Bean) = dao.insertBean(bean) // Korrekt funktion från DAO
    override suspend fun updateBean(bean: Bean) = dao.updateBean(bean) // Korrekt funktion från DAO
    override suspend fun getBeanById(id: Long): Bean? = dao.getBeanById(id) // Använder Long för ID, matchar Entity

    // --- Brew ---
    override suspend fun addBrew(brew: Brew): Long = dao.insertBrew(brew) // Korrekt funktion från DAO
    override suspend fun addBrewSamples(samples: List<BrewSample>) = dao.insertBrewSamples(samples)
    override fun getBrewMetrics(brewId: Long): Flow<BrewMetrics?> = dao.getBrewMetrics(brewId) // Korrekt funktion från DAO
}

