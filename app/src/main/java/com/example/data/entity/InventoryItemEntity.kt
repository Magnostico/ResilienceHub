package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.ItemCategory
import com.example.data.model.StorageLocation

@Entity(tableName = "inventory_items")
data class InventoryItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val barcode: String? = null,
    val category: ItemCategory,
    val location: StorageLocation,
    val quantity: Double,
    val unit: String,
    val expirationDate: Long? = null, // epoch millis
    val lastMaintenanceDate: Long? = null, // epoch millis for battery/gear recharge cycles
    val alertDaysBefore: Int = 90
)
