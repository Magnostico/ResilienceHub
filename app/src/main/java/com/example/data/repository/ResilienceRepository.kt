package com.example.data.repository

import com.example.data.AppDatabase
import com.example.data.OfficialSurvivalKnowledgeBase
import com.example.data.entity.FamilyProfileEntity
import com.example.data.entity.InventoryItemEntity
import com.example.data.entity.ManualChunkFtsEntity
import com.example.data.entity.MapPoiEntity
import com.example.data.model.ItemCategory
import com.example.data.model.PoiType
import com.example.data.model.StorageLocation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

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

    suspend fun clearAllItems() =
        database.inventoryDao().clearAll()

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

    suspend fun deleteProfileById(id: Long) =
        database.familyProfileDao().deleteById(id)

    suspend fun clearAllProfiles() =
        database.familyProfileDao().clearAll()

    // --- Map POIs ---
    val allPois: Flow<List<MapPoiEntity>> = database.mapPoiDao().getAllPois()

    val familyMeetingPois: Flow<List<MapPoiEntity>> = database.mapPoiDao().getFamilyMeetingPois()

    fun getPoisByType(type: PoiType): Flow<List<MapPoiEntity>> =
        database.mapPoiDao().getPoisByType(type)

    suspend fun insertPoi(poi: MapPoiEntity): Long =
        database.mapPoiDao().insertPoi(poi)

    suspend fun deletePoi(poi: MapPoiEntity) =
        database.mapPoiDao().deletePoi(poi)

    suspend fun deletePoiById(id: Long) =
        database.mapPoiDao().deleteById(id)

    suspend fun clearAllPois() =
        database.mapPoiDao().clearAll()

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

    suspend fun deleteManualChunk(rowId: Int) =
        database.manualChunkDao().deleteChunk(rowId)

    suspend fun clearAllManualChunks() =
        database.manualChunkDao().clearAll()

    /**
     * Garante que a Base Oficial de Sobrevivência (30 diretrizes dos Órgãos Oficiais)
     * esteja sempre populada no banco de dados (mesmo em atualizações sobre instalações anteriores).
     */
    suspend fun ensureOfficialKnowledgeBaseSeeded() {
        val currentChunks = database.manualChunkDao().getAllChunks().first()
        if (currentChunks.size < OfficialSurvivalKnowledgeBase.MIN_EXPECTED_OFFICIAL_CHUNKS) {
            val customChunks = currentChunks.filter { it.rowId > 100 }
            database.manualChunkDao().clearAll()
            database.manualChunkDao().insertAll(OfficialSurvivalKnowledgeBase.getOfficialChunks())
            if (customChunks.isNotEmpty()) {
                database.manualChunkDao().insertAll(customChunks)
            }
        }
    }

    suspend fun restoreOfficialKnowledgeBase() {
        database.manualChunkDao().clearAll()
        database.manualChunkDao().insertAll(OfficialSurvivalKnowledgeBase.getOfficialChunks())
    }

    suspend fun clearAllDatabaseData() {
        database.inventoryDao().clearAll()
        database.familyProfileDao().clearAll()
        database.mapPoiDao().clearAll()
        // Mantém a Base Oficial de Sobrevivência dos órgãos oficiais para o Chat IA continuar funcionando!
        database.manualChunkDao().clearAll()
        database.manualChunkDao().insertAll(OfficialSurvivalKnowledgeBase.getOfficialChunks())
    }
}
