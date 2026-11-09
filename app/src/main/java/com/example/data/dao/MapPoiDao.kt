package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.entity.MapPoiEntity
import com.example.data.model.PoiType
import kotlinx.coroutines.flow.Flow

@Dao
interface MapPoiDao {
    @Query("SELECT * FROM map_pois ORDER BY title ASC")
    fun getAllPois(): Flow<List<MapPoiEntity>>

    @Query("SELECT * FROM map_pois WHERE type = :type ORDER BY title ASC")
    fun getPoisByType(type: PoiType): Flow<List<MapPoiEntity>>

    @Query("SELECT * FROM map_pois WHERE type = 'FAMILY_MEETING' ORDER BY id ASC")
    fun getFamilyMeetingPois(): Flow<List<MapPoiEntity>>

    @Query("SELECT * FROM map_pois WHERE id = :id LIMIT 1")
    fun getPoiById(id: Long): Flow<MapPoiEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoi(poi: MapPoiEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(pois: List<MapPoiEntity>)

    @Update
    suspend fun updatePoi(poi: MapPoiEntity)

    @Delete
    suspend fun deletePoi(poi: MapPoiEntity)

    @Query("DELETE FROM map_pois WHERE id = :id")
    suspend fun deleteById(id: Long)
}
