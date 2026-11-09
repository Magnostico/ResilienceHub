package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.PoiType

@Entity(tableName = "map_pois")
data class MapPoiEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val type: PoiType,
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val phone: String? = null
)
