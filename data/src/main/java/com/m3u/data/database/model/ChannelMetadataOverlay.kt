package com.m3u.data.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "channel_metadata_bases",
    primaryKeys = ["playlist_url", "channel_reference"],
    foreignKeys = [
        ForeignKey(
            entity = Playlist::class,
            parentColumns = ["url"],
            childColumns = ["playlist_url"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index("playlist_url"),
    ],
)
data class ChannelMetadataBase(
    @ColumnInfo(name = "playlist_url")
    val playlistUrl: String,
    @ColumnInfo(name = "channel_reference")
    val channelReference: String,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "category")
    val category: String,
)

@Entity(
    tableName = "extension_channel_metadata_overlays",
    primaryKeys = ["playlist_url", "channel_reference", "extension_id"],
    foreignKeys = [
        ForeignKey(
            entity = ChannelMetadataBase::class,
            parentColumns = ["playlist_url", "channel_reference"],
            childColumns = ["playlist_url", "channel_reference"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index(value = ["playlist_url", "channel_reference"]),
        Index("extension_id"),
    ],
)
data class ExtensionChannelMetadataOverlay(
    @ColumnInfo(name = "playlist_url")
    val playlistUrl: String,
    @ColumnInfo(name = "channel_reference")
    val channelReference: String,
    @ColumnInfo(name = "extension_id")
    val extensionId: String,
    @ColumnInfo(name = "title")
    val title: String?,
    @ColumnInfo(name = "category")
    val category: String?,
)
