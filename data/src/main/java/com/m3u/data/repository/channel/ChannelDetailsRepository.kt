package com.m3u.data.repository.channel

import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.ChannelDetails
import kotlinx.coroutines.flow.Flow

/**
 * Synopsis, cast and rating for a title — fetched once, then read from cache.
 *
 * Xtream keeps this out of the catalogue: a channel list carries names and
 * artwork only, so each description costs its own request. Panels here allow a
 * single connection at a time, so requests are made one by one and only when
 * something actually needs them.
 */
interface ChannelDetailsRepository {
    /**
     * Emits what is already known, then whatever the panel adds.
     *
     * Emits null for channels that cannot carry a description at all — live
     * channels, or anything without a stable reference — so callers can tell
     * "nothing to show" from "not fetched yet".
     */
    fun observe(channel: Channel): Flow<ChannelDetails?>

    /**
     * Fetches the description unless it is already cached.
     *
     * Returns whatever is known afterwards, cached or fresh. Failures return
     * the cached value rather than throwing: a missing synopsis must never
     * take a sheet down with it.
     */
    suspend fun fetchIfMissing(channel: Channel): ChannelDetails?
}
