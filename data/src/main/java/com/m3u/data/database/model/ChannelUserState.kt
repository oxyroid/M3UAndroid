package com.m3u.data.database.model

import androidx.room.ColumnInfo

/**
 * What a viewer built up on a channel, as opposed to what the provider says
 * about it.
 *
 * Refreshing a catalogue deletes every channel and imports them anew, so rows
 * come back with their id regenerated and these columns reset — the Continue
 * watching row empties, favourites are lost. Carried across the import, none of
 * that is.
 */
data class ChannelUserState(
    /** Stable across imports when the provider gives one; M3U often does not. */
    @ColumnInfo(name = "relation_id")
    val relationId: String?,
    @ColumnInfo(name = "url")
    val url: String,
    @ColumnInfo(name = "seen")
    val seen: Long,
    @ColumnInfo(name = "favourite")
    val favourite: Boolean,
    @ColumnInfo(name = "hidden")
    val hidden: Boolean,
)

/**
 * Looks a channel up by whichever identity survived the import.
 *
 * Xtream channels keep a relation id; an M3U playlist without tvg-id has none,
 * and only its URL to go on. Falling back to the URL keeps both kinds covered
 * without the callers having to know which is which.
 */
class PreservedUserStates(states: List<ChannelUserState>) {
    private val byRelationId: Map<String, ChannelUserState> = states
        .mapNotNull { state ->
            state.relationId?.takeIf(String::isNotBlank)?.let { id -> id to state }
        }
        .toMap()

    private val byUrl: Map<String, ChannelUserState> = states.associateBy(ChannelUserState::url)

    val isEmpty: Boolean get() = byRelationId.isEmpty() && byUrl.isEmpty()

    fun of(relationId: String?, url: String): ChannelUserState? =
        relationId?.takeIf(String::isNotBlank)?.let(byRelationId::get) ?: byUrl[url]
}

/**
 * Hands a freshly imported channel back what its predecessor had earned.
 *
 * Returns the channel untouched when nothing was preserved for it, which is the
 * case for all but a handful of a catalogue.
 */
fun Channel.restoring(states: PreservedUserStates?, relationId: String?): Channel {
    val state = states?.of(relationId ?: this.relationId, url) ?: return this
    return copy(seen = state.seen, favourite = state.favourite, hidden = state.hidden)
}
