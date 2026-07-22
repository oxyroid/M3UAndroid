package com.m3u.data.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "provider_playback_sessions",
    foreignKeys = [
        ForeignKey(
            entity = ProviderAccount::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        )
    ],
    indices = [Index(value = ["account_id"])],
)
data class ProviderPlaybackSessionEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "account_id")
    val accountId: String,
    @ColumnInfo(name = "provider_id")
    val providerId: String,
    @ColumnInfo(name = "item_id")
    val itemId: String,
    @ColumnInfo(name = "media_source_id")
    val mediaSourceId: String?,
    @ColumnInfo(name = "source_type")
    val sourceType: String,
    @ColumnInfo(name = "fallback_direct_url")
    val fallbackDirectUrl: String?,
    @ColumnInfo(name = "play_session_id")
    val playSessionId: String?,
    @ColumnInfo(name = "live_stream_id")
    val liveStreamId: String?,
    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long,
)
