package com.victorkoffed.projektandroid.data.repository.implementation

import com.victorkoffed.projektandroid.data.db.Grinder
import com.victorkoffed.projektandroid.data.db.CoffeeDao
import com.victorkoffed.projektandroid.data.repository.interfaces.GrinderRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GrinderRepositoryImpl @Inject constructor(
    private val dao: CoffeeDao
) : GrinderRepository {
    override fun getAllGrinders(): Flow<List<Grinder>> = dao.getAllGrinders()
    override suspend fun addGrinder(grinder: Grinder) = dao.insertGrinder(grinder)
    override suspend fun updateGrinder(grinder: Grinder) = dao.updateGrinder(grinder)
    override suspend fun deleteGrinder(grinder: Grinder) = dao.deleteGrinder(grinder)
    override suspend fun getGrinderById(id: Long): Grinder? = dao.getGrinderById(id)
}