package com.victorkoffed.projektandroid.data.repository.implementation

import com.victorkoffed.projektandroid.data.db.Method
import com.victorkoffed.projektandroid.data.db.CoffeeDao
import com.victorkoffed.projektandroid.data.repository.interfaces.MethodRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MethodRepositoryImpl @Inject constructor(
    private val dao: CoffeeDao
) : MethodRepository {
    override fun getAllMethods(): Flow<List<Method>> = dao.getAllMethods()
    override suspend fun addMethod(method: Method) = dao.insertMethod(method)
    override suspend fun updateMethod(method: Method) = dao.updateMethod(method)
    override suspend fun deleteMethod(method: Method) = dao.deleteMethod(method)
    override suspend fun getMethodById(id: Long): Method? = dao.getMethodById(id)
}