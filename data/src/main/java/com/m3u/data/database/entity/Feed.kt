package com.m3u.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

// called playlist in user interface
@Entity(tableName = "feeds")
data class Feed(
    @ColumnInfo(name = "title")
    val title: String,
    @PrimaryKey
    @ColumnInfo(name = "url")
    val url: String
) {
    fun isTemplated(): Boolean = url == URL_IMPORTED

    companion object {
        const val URL_IMPORTED = "imported"
    }
}
