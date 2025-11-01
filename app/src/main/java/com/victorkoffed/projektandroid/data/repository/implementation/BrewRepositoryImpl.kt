package com.victorkoffed.projektandroid.data.repository.implementation

import com.victorkoffed.projektandroid.data.db.Brew
import com.victorkoffed.projektandroid.data.db.BrewMetrics
import com.victorkoffed.projektandroid.data.db.BrewSample
import com.victorkoffed.projektandroid.data.db.CoffeeDao
import com.victorkoffed.projektandroid.data.repository.interfaces.BrewRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BrewRepositoryImpl @Inject constructor(
    private val dao: CoffeeDao
) : BrewRepository {
    override fun getAllBrews(): Flow<List<Brew>> = dao.getAllBrews()
    override fun getAllBrewsIncludingArchived(): Flow<List<Brew>> = dao.getAllBrewsIncludingArchived()
    override suspend fun getBrewById(id: Long): Brew? = dao.getBrewById(id)
    override suspend fun addBrew(brew: Brew): Long = dao.addBrew(brew)
    override suspend fun updateBrew(brew: Brew) = dao.updateBrew(brew)
    override suspend fun deleteBrew(brew: Brew) = dao.deleteBrew(brew)
    override suspend fun deleteBrewAndRestoreStock(brew: Brew) = dao.deleteBrewTransaction(brew)
    override suspend fun addBrewWithSamples(brew: Brew, samples: List<BrewSample>): Long =
        dao.addBrewWithSamples(brew, samples)
    override fun observeBrew(brewId: Long): Flow<Brew?> = dao.observeBrew(brewId)
    override fun getBrewsForBean(beanId: Long): Flow<List<Brew>> = dao.getBrewsForBean(beanId)
    override fun getTotalBrewCount(): Flow<Int> = dao.getTotalBrewCount()
    override fun getSamplesForBrew(brewId: Long): Flow<List<BrewSample>> = dao.getSamplesForBrew(brewId)
    override suspend fun addBrewSamples(samples: List<BrewSample>) = dao.insertBrewSamples(samples)
    override fun getBrewMetrics(brewId: Long): Flow<BrewMetrics?> = dao.getBrewMetrics(brewId)
}