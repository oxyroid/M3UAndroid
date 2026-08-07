package com.m3u.data.database.model

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Embedded

/**
 * A search hit, and why it is one.
 *
 * Searching only titles needs no explanation. Once cast lists are searched
 * too, a query like "Gyllenhaal" returns films whose titles share nothing
 * with it, and a plain list of them reads as a bug — so the matching cast is
 * carried alongside and shown.
 */
@Immutable
data class ChannelSearchResult(
    @Embedded
    val channel: Channel,
    /** Null when the title matched; the cast list when only an actor did. */
    @ColumnInfo(name = "matched_cast")
    val matchedCast: String? = null,
)
