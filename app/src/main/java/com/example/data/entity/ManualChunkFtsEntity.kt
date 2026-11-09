package com.example.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

@Fts4
@Entity(tableName = "manual_chunks_fts")
data class ManualChunkFtsEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "rowid")
    val rowId: Int = 0,
    val docTitle: String,
    val category: String,
    val pageNumber: Int,
    val contentText: String
)
