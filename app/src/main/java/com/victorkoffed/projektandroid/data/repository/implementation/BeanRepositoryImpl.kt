package com.victorkoffed.projektandroid.data.repository.implementation

import com.victorkoffed.projektandroid.data.db.Bean
import com.victorkoffed.projektandroid.data.db.CoffeeDao
import com.victorkoffed.projektandroid.data.repository.interfaces.BeanRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Concrete implementation of [BeanRepository] bridging the domain layer and the local database.
 * Mediates data operations for coffee bean inventory management, including lifecycle states
 * like archiving to preserve historical brew integrity without cluttering active selections.
 */
@Singleton
class BeanRepositoryImpl @Inject constructor(
    private val dao: CoffeeDao
) : BeanRepository {
    override fun getAllBeans(): Flow<List<Bean>> = dao.getAllBeans()
    override fun getArchivedBeans(): Flow<List<Bean>> = dao.getArchivedBeans()
    override suspend fun addBean(bean: Bean) = dao.insertBean(bean)
    override suspend fun updateBean(bean: Bean) = dao.updateBean(bean)
    override suspend fun getBeanById(id: Long): Bean? = dao.getBeanById(id)
    override fun observeBean(beanId: Long): Flow<Bean?> = dao.observeBean(beanId)
    override suspend fun deleteBean(bean: Bean) = dao.deleteBean(bean)
    override suspend fun updateBeanArchivedStatus(id: Long, isArchived: Boolean) =
        dao.updateBeanArchivedStatus(id, isArchived)
    override fun getTotalBeanCount(): Flow<Int> = dao.getTotalBeanCount()
}