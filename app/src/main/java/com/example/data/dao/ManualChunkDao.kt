package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.entity.ManualChunkFtsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ManualChunkDao {
    @Query("""
        SELECT rowid, docTitle, category, pageNumber, contentText 
        FROM manual_chunks_fts 
        WHERE manual_chunks_fts MATCH :searchQuery
    """)
    fun searchChunks(searchQuery: String): Flow<List<ManualChunkFtsEntity>>

    @Query("""
        SELECT rowid, docTitle, category, pageNumber, contentText 
        FROM manual_chunks_fts 
        WHERE category = :category
    """)
    fun getChunksByCategory(category: String): Flow<List<ManualChunkFtsEntity>>

    @Query("SELECT rowid, docTitle, category, pageNumber, contentText FROM manual_chunks_fts")
    fun getAllChunks(): Flow<List<ManualChunkFtsEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunk(chunk: ManualChunkFtsEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(chunks: List<ManualChunkFtsEntity>)

    @Query("DELETE FROM manual_chunks_fts WHERE rowid = :rowId")
    suspend fun deleteChunk(rowId: Int)

    @Query("DELETE FROM manual_chunks_fts")
    suspend fun clearAll()
}
