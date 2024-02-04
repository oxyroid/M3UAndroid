package com.m3u.data.database

import androidx.room.TypeConverter
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
}