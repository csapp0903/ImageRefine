package com.imagerefine.app.data.db

import androidx.room.*
import com.imagerefine.app.data.model.FilterPreset
import kotlinx.coroutines.flow.Flow

@Dao
interface FilterDao {
    @Query("SELECT * FROM filter_presets ORDER BY createdAt DESC")
    fun getAllFilters(): Flow<List<FilterPreset>>

    @Query("SELECT * FROM filter_presets WHERE isBuiltIn = 0 ORDER BY createdAt DESC")
    fun getUserFilters(): Flow<List<FilterPreset>>

    @Query("SELECT * FROM filter_presets WHERE isBuiltIn = 1")
    fun getBuiltInFilters(): Flow<List<FilterPreset>>

    @Query("SELECT * FROM filter_presets WHERE id = :id")
    suspend fun getFilterById(id: Long): FilterPreset?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(filter: FilterPreset): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(filters: List<FilterPreset>)

    @Delete
    suspend fun delete(filter: FilterPreset)

    @Query("DELETE FROM filter_presets WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM filter_presets WHERE isBuiltIn = 1")
    suspend fun getBuiltInCount(): Int
}
