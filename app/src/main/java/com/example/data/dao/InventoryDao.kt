package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.entity.InventoryItemEntity
import com.example.data.model.ItemCategory
import com.example.data.model.StorageLocation
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryDao {
    @Query("SELECT * FROM inventory_items ORDER BY expirationDate ASC NULLS LAST")
    fun getAllItems(): Flow<List<InventoryItemEntity>>

    @Query("SELECT * FROM inventory_items WHERE location = :location ORDER BY expirationDate ASC NULLS LAST")
    fun getItemsByLocation(location: StorageLocation): Flow<List<InventoryItemEntity>>

    @Query("SELECT * FROM inventory_items WHERE category = :category ORDER BY expirationDate ASC NULLS LAST")
    fun getItemsByCategory(category: ItemCategory): Flow<List<InventoryItemEntity>>

    @Query("SELECT * FROM inventory_items WHERE id = :id LIMIT 1")
    fun getItemById(id: Long): Flow<InventoryItemEntity?>

    @Query("SELECT * FROM inventory_items WHERE barcode = :barcode LIMIT 1")
    suspend fun findByBarcode(barcode: String): InventoryItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: InventoryItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<InventoryItemEntity>)

    @Update
    suspend fun updateItem(item: InventoryItemEntity)

    @Delete
    suspend fun deleteItem(item: InventoryItemEntity)

    @Query("DELETE FROM inventory_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE inventory_items SET quantity = :newQuantity WHERE id = :id")
    suspend fun updateQuantity(id: Long, newQuantity: Double)

    @Query("UPDATE inventory_items SET lastMaintenanceDate = :timestamp WHERE id = :id")
    suspend fun updateMaintenanceDate(id: Long, timestamp: Long)
}
