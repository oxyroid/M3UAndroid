package com.m3u.data.database.model

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(
    tableName = "watch_progress"
)
@Immutable
@Serializable
data class WatchProgress(
    @PrimaryKey
    @ColumnInfo(name = "channel_id")
    val channelId: Int,
    @ColumnInfo(name = "position")
    val position: Long, // playback position in milliseconds
    @ColumnInfo(name = "duration")
    val duration: Long, // total duration in milliseconds
    @ColumnInfo(name = "last_watched")
    val lastWatched: Long, // timestamp when last watched
    @ColumnInfo(name = "playlist_url")
    val playlistUrl: String // to identify which playlist it belongs to
)
