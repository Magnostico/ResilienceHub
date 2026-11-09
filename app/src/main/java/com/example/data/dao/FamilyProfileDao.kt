package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.entity.FamilyProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FamilyProfileDao {
    @Query("SELECT * FROM family_profiles ORDER BY role ASC, name ASC")
    fun getAllProfiles(): Flow<List<FamilyProfileEntity>>

    @Query("SELECT * FROM family_profiles WHERE id = :id LIMIT 1")
    fun getProfileById(id: Long): Flow<FamilyProfileEntity?>

    @Query("SELECT COUNT(*) FROM family_profiles WHERE role = 'ADULT'")
    fun getAdultCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM family_profiles WHERE role = 'CHILD'")
    fun getChildCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: FamilyProfileEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(profiles: List<FamilyProfileEntity>)

    @Update
    suspend fun updateProfile(profile: FamilyProfileEntity)

    @Delete
    suspend fun deleteProfile(profile: FamilyProfileEntity)

    @Query("DELETE FROM family_profiles WHERE id = :id")
    suspend fun deleteById(id: Long)
}
