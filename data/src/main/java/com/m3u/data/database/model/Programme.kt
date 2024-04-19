package com.m3u.data.database.model

import androidx.annotation.Keep
import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "programmes")
@Immutable
@Keep
// for epg
// dynamic only when it is from xtream live.
data class Programme(
    @ColumnInfo(name = "stream_id")
    val streamId: Int,
    @ColumnInfo(name = "start")
    val start: Long,
    @ColumnInfo(name = "end")
    val end: Long,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "description")
    val description: String,
    @ColumnInfo(name = "icon")
    val icon: String? = null,
    @ColumnInfo(name = "categories")
    val categories: List<String>,
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Int = 0
)