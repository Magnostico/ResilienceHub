package com.example.data.repository

import com.example.data.AppDatabase
import com.example.data.entity.FamilyProfileEntity
import com.example.data.entity.InventoryItemEntity
import com.example.data.entity.ManualChunkFtsEntity
import com.example.data.entity.MapPoiEntity
import com.example.data.model.ItemCategory
import com.example.data.model.PoiType
import com.example.data.model.StorageLocation
import kotlinx.coroutines.flow.Flow

class ResilienceRepository(private val database: AppDatabase) {

    // --- Inventory ---
    val allItems: Flow<List<InventoryItemEntity>> = database.inventoryDao().getAllItems()

    fun getItemsByLocation(location: StorageLocation): Flow<List<InventoryItemEntity>> =
        database.inventoryDao().getItemsByLocation(location)

    fun getItemsByCategory(category: ItemCategory): Flow<List<InventoryItemEntity>> =
        database.inventoryDao().getItemsByCategory(category)

    fun getItemById(id: Long): Flow<InventoryItemEntity?> =
        database.inventoryDao().getItemById(id)

    suspend fun insertItem(item: InventoryItemEntity): Long =
        database.inventoryDao().insertItem(item)

    suspend fun updateItem(item: InventoryItemEntity) =
        database.inventoryDao().updateItem(item)

    suspend fun deleteItemById(id: Long) =
        database.inventoryDao().deleteById(id)

    suspend fun updateQuantity(id: Long, newQuantity: Double) =
        database.inventoryDao().updateQuantity(id, newQuantity)

    suspend fun registerMaintenance(id: Long, timestamp: Long = System.currentTimeMillis()) =
        database.inventoryDao().updateMaintenanceDate(id, timestamp)

    // --- Family Profiles ---
    val allProfiles: Flow<List<FamilyProfileEntity>> = database.familyProfileDao().getAllProfiles()

    suspend fun insertProfile(profile: FamilyProfileEntity): Long =
        database.familyProfileDao().insertProfile(profile)

    suspend fun updateProfile(profile: FamilyProfileEntity) =
        database.familyProfileDao().updateProfile(profile)

    suspend fun deleteProfile(profile: FamilyProfileEntity) =
        database.familyProfileDao().deleteProfile(profile)

    // --- Map POIs ---
    val allPois: Flow<List<MapPoiEntity>> = database.mapPoiDao().getAllPois()

    val familyMeetingPois: Flow<List<MapPoiEntity>> = database.mapPoiDao().getFamilyMeetingPois()

    fun getPoisByType(type: PoiType): Flow<List<MapPoiEntity>> =
        database.mapPoiDao().getPoisByType(type)

    suspend fun insertPoi(poi: MapPoiEntity): Long =
        database.mapPoiDao().insertPoi(poi)

    suspend fun deletePoi(poi: MapPoiEntity) =
        database.mapPoiDao().deletePoi(poi)

    // --- FTS Manuals ---
    val allManualChunks: Flow<List<ManualChunkFtsEntity>> = database.manualChunkDao().getAllChunks()

    fun searchManual(query: String): Flow<List<ManualChunkFtsEntity>> {
        val cleanTokens = query.replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .trim()
            .split(Regex("\\s+"))
            .filter { it.length >= 2 }
        return if (cleanTokens.isEmpty()) {
            database.manualChunkDao().getAllChunks()
        } else {
            val ftsQuery = cleanTokens.joinToString(" OR ") { "$it*" }
            database.manualChunkDao().searchChunks(ftsQuery)
        }
    }

    suspend fun insertChunk(chunk: ManualChunkFtsEntity): Long =
        database.manualChunkDao().insertChunk(chunk)
}
