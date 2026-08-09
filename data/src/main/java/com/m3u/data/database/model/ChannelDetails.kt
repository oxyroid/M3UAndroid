package com.m3u.data.database.model

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * Synopsis, cast and rating for one title, kept so the panel is asked once.
 *
 * Keyed on (playlist, channel reference) rather than on the channel row id.
 * Ids are regenerated whenever a playlist is re-subscribed — which is the only
 * way to refresh a catalogue — so keying on them would throw the whole cache
 * away every time, and re-earning it costs one request per title.
 */
/*
 * Deliberately without a foreign key onto playlists.
 *
 * Re-subscribing writes the playlist row back with INSERT OR REPLACE, and
 * SQLite implements REPLACE as a delete followed by an insert — so an
 * ON DELETE CASCADE here would empty this table on every refresh. Measured on
 * a real database before this was caught: 401 rows before, 0 after, each one
 * costing a network request to earn back.
 *
 * Rows are removed explicitly when a playlist is actually unsubscribed, next
 * to where its channels are deleted.
 */
@Entity(
    tableName = "channel_details",
    primaryKeys = ["playlist_url", "channel_reference"],
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
