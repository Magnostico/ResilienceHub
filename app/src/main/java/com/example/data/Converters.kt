package com.example.data

import androidx.room.TypeConverter
import com.example.data.model.FamilyRole
import com.example.data.model.ItemCategory
import com.example.data.model.PoiType
import com.example.data.model.StorageLocation

class Converters {
    @TypeConverter
    fun fromItemCategory(value: ItemCategory?): String? = value?.name

    @TypeConverter
    fun toItemCategory(value: String?): ItemCategory? =
        value?.let { runCatching { ItemCategory.valueOf(it) }.getOrDefault(ItemCategory.HYGIENE_DOCS) }

    @TypeConverter
    fun fromStorageLocation(value: StorageLocation?): String? = value?.name

    @TypeConverter
    fun toStorageLocation(value: String?): StorageLocation? =
        value?.let { runCatching { StorageLocation.valueOf(it) }.getOrDefault(StorageLocation.APARTMENT) }

    @TypeConverter
    fun fromPoiType(value: PoiType?): String? = value?.name

    @TypeConverter
    fun toPoiType(value: String?): PoiType? =
        value?.let { runCatching { PoiType.valueOf(it) }.getOrDefault(PoiType.FAMILY_MEETING) }

    @TypeConverter
    fun fromFamilyRole(value: FamilyRole?): String? = value?.name

    @TypeConverter
    fun toFamilyRole(value: String?): FamilyRole? =
        value?.let { runCatching { FamilyRole.valueOf(it) }.getOrDefault(FamilyRole.ADULT) }
}
