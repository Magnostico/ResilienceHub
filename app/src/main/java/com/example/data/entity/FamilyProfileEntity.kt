package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.FamilyRole

@Entity(tableName = "family_profiles")
data class FamilyProfileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val role: FamilyRole,
    val weightKg: Double,
    val bloodType: String,
    val allergies: String,
    val medications: String
)
