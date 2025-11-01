package com.victorkoffed.projektandroid.data.repository.interfaces

import com.victorkoffed.projektandroid.data.db.Bean
import kotlinx.coroutines.flow.Flow

/**
 * Interface för att hantera dataoperationer relaterade till Kaffebönor (Bean).
 */
interface BeanRepository {
    fun getAllBeans(): Flow<List<Bean>>
    fun getArchivedBeans(): Flow<List<Bean>>
    suspend fun addBean(bean: Bean)
    suspend fun updateBean(bean: Bean)
    suspend fun getBeanById(id: Long): Bean?
    fun observeBean(beanId: Long): Flow<Bean?>
    suspend fun deleteBean(bean: Bean)
    suspend fun updateBeanArchivedStatus(id: Long, isArchived: Boolean)
    fun getTotalBeanCount(): Flow<Int>
}