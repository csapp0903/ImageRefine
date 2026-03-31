package com.imagerefine.app.data.repository

import com.imagerefine.app.data.db.FilterDao
import com.imagerefine.app.data.model.FilterPreset
import kotlinx.coroutines.flow.Flow

class FilterRepository(private val filterDao: FilterDao) {

    fun getAllFilters(): Flow<List<FilterPreset>> = filterDao.getAllFilters()

    fun getUserFilters(): Flow<List<FilterPreset>> = filterDao.getUserFilters()

    fun getBuiltInFilters(): Flow<List<FilterPreset>> = filterDao.getBuiltInFilters()

    suspend fun getFilterById(id: Long): FilterPreset? = filterDao.getFilterById(id)

    suspend fun saveFilter(filter: FilterPreset): Long = filterDao.insert(filter)

    suspend fun deleteFilter(filter: FilterPreset) = filterDao.delete(filter)

    suspend fun deleteFilterById(id: Long) = filterDao.deleteById(id)

    suspend fun initBuiltInFilters() {
        if (filterDao.getBuiltInCount() == 0) {
            filterDao.insertAll(FilterPreset.builtInFilters)
        }
    }
}
