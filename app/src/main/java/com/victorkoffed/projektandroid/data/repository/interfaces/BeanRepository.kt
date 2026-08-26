package com.victorkoffed.projektandroid.data.repository.interfaces

import com.victorkoffed.projektandroid.data.db.Bean
import kotlinx.coroutines.flow.Flow

/**
 * Defines the data operations for coffee bean inventory management.
 * Abstracts the underlying data source and provides reactive streams for observing
 * bean lifecycles, including active stock and archival states.
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