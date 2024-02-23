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
    fun fromDataSource(from: DataSource): Int = when (from) {
        DataSource.M3U -> 0
        DataSource.Xtream -> 1
        DataSource.Emby -> 2
        DataSource.Dropbox -> 3
        DataSource.Aliyun -> 4
    }

    @TypeConverter
    fun toDataSource(to: Int): DataSource = when (to) {
        0 -> DataSource.M3U
        1 -> DataSource.Xtream
        2 -> DataSource.Emby
        3 -> DataSource.Dropbox
        4 -> DataSource.Aliyun
        else -> throw RuntimeException("Unsupported playlist data source $to.")
    }
}