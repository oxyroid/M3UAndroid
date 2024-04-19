package com.m3u.data.database.model

import androidx.annotation.Keep
import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "episodes")
@Immutable
@Keep
// for series type streams
data class Episode(
    @ColumnInfo(name = "title")
    val title: String,
    // series is a special stream
    // if a playlist type is one of [Playlist.SERIES_TYPES]
    // then its all streams are series.
    @ColumnInfo(name = "series_id")
    val seriesId: Int,
    @ColumnInfo(name = "season")
    val season: String,
    @ColumnInfo(name = "number")
    val number: Int,
    @ColumnInfo(name = "url")
    val url: String,
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int = 0
)