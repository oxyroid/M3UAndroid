package com.m3u.data.database

import androidx.room.TypeConverter
import com.m3u.data.database.model.DataSource
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object Converters {
    @TypeConverter
    fun fromStringList(from: List<String>): String {
        return Json.encodeToString(from)
    }

    @TypeConverter
    fun toStringList(to: String): List<String> {
        return Json.decodeFromString(to)
    }

    @TypeConverter
    fun fromDataSource(from: DataSource): String = from.value

    @TypeConverter
    fun toDataSource(to: String): DataSource = DataSource.of(to)
}