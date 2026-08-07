package com.m3u.data.database.model

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Synopsis, cast and rating for one title, kept so the panel is asked once.
 *
 * Keyed on (playlist, channel reference) rather than on the channel row id.
 * Ids are regenerated whenever a playlist is re-subscribed — which is the only
 * way to refresh a catalogue — so keying on them would throw the whole cache
 * away every time, and re-earning it costs one request per title.
 */
@Entity(
    tableName = "channel_details",
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
@Immutable
data class ChannelDetails(
    @ColumnInfo(name = "playlist_url")
    val playlistUrl: String,
    @ColumnInfo(name = "channel_reference")
    val channelReference: String,
    @ColumnInfo(name = "plot")
    val plot: String? = null,
    /**
     * ⚠️ `cast` is an SQL keyword. Room quotes it in everything it generates,
     * but any hand-written query naming this column must quote it too.
     */
    @ColumnInfo(name = "cast")
    val cast: String? = null,
    /**
     * [cast] folded the way titles are, so searching an actor can reuse the
     * same comparison. Filled even when nothing searches it yet.
     */
    @ColumnInfo(name = "cast_normalized")
    val castNormalized: String? = null,
    @ColumnInfo(name = "director")
    val director: String? = null,
    @ColumnInfo(name = "genre")
    val genre: String? = null,
    @ColumnInfo(name = "rating")
    val rating: String? = null,
    @ColumnInfo(name = "release_date")
    val releaseDate: String? = null,
    @ColumnInfo(name = "duration_seconds")
    val durationSeconds: Int? = null,
    /**
     * When the panel was last asked, epoch millis.
     *
     * Also records the answer "this title has no description": without it, a
     * blank row is indistinguishable from one never fetched, and the sheet
     * would re-ask on every single open.
     */
    @ColumnInfo(name = "fetched_at")
    val fetchedAt: Long,
)
