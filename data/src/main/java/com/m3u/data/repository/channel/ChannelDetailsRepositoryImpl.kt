package com.m3u.data.repository.channel

import com.m3u.core.foundation.util.basic.normalizeForSearch
import com.m3u.data.database.dao.ChannelDetailsDao
import com.m3u.data.database.dao.PlaylistDao
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.ChannelDetails
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.isSeries
import com.m3u.data.database.model.isVod
import com.m3u.data.parser.xtream.XtreamChannelDetails
import com.m3u.data.parser.xtream.XtreamInput
import com.m3u.data.parser.xtream.XtreamParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock

@Singleton
internal class ChannelDetailsRepositoryImpl @Inject constructor(
    private val channelDetailsDao: ChannelDetailsDao,
    private val playlistDao: PlaylistDao,
    private val xtreamParser: XtreamParser,
) : ChannelDetailsRepository {
    private val timber = Timber.tag("ChannelDetailsRepository")

    /**
     * Serialises outgoing requests across the whole app.
     *
     * Xtream accounts commonly allow a single connection, and this one is
     * shared with playback: firing several description requests at once can
     * cost the viewer their stream. One at a time, always.
     */
    private val requestLock = Mutex()

    override fun observe(channel: Channel): Flow<ChannelDetails?> {
        val reference = channel.relationId?.takeIf(String::isNotBlank)
            ?: return flowOf(null)
        return channelDetailsDao
            .observe(channel.playlistUrl, reference)
            .catch { throwable ->
                timber.w(throwable, "cannot observe details")
                emit(null)
            }
    }

    override suspend fun fetchIfMissing(channel: Channel): ChannelDetails? {
        val reference = channel.relationId?.takeIf(String::isNotBlank) ?: return null
        channelDetailsDao.get(channel.playlistUrl, reference)?.let { return it }

        val playlist = playlistDao.get(channel.playlistUrl) ?: return null
        val kind = playlist.channelDetailsKind() ?: return null
        val id = reference.toIntOrNull() ?: return null
        val input = runCatching { XtreamInput.decodeFromPlaylistUrl(playlist.url) }
            .getOrNull() ?: return null

        val fetched = requestLock.withLock {
            // Another sheet may have fetched the very same title while this one
            // waited its turn.
            channelDetailsDao.get(channel.playlistUrl, reference)?.let { return it }
            runCatching { xtreamParser.getChannelDetailsOrNull(input, kind, id) }
                .onFailure { throwable -> timber.w(throwable, "cannot fetch details") }
                .getOrNull()
        }

        val details = fetched.toEntity(
            playlistUrl = channel.playlistUrl,
            channelReference = reference,
        )
        // Stored even when the panel returned nothing: the timestamp is what
        // distinguishes "asked, has no description" from "never asked", and
        // without it every open of the sheet would ask again.
        runCatching { channelDetailsDao.upsert(details) }
            .onFailure { throwable -> timber.w(throwable, "cannot store details") }
        return details
    }

    private fun Playlist.channelDetailsKind(): XtreamParser.ChannelDetailsKind? = when {
        isVod -> XtreamParser.ChannelDetailsKind.Vod
        isSeries -> XtreamParser.ChannelDetailsKind.Series
        // Live channels have no description to fetch.
        else -> null
    }

    private fun XtreamChannelDetails?.toEntity(
        playlistUrl: String,
        channelReference: String,
    ): ChannelDetails {
        val info = this?.info
        val cast = info?.cast?.takeIf(String::isNotBlank)
        return ChannelDetails(
            playlistUrl = playlistUrl,
            channelReference = channelReference,
            plot = info?.plot?.takeIf(String::isNotBlank),
            cast = cast,
            castNormalized = cast?.normalizeForSearch(),
            director = info?.director?.takeIf(String::isNotBlank),
            genre = info?.genre?.takeIf(String::isNotBlank),
            rating = info?.rating?.takeIf(String::isNotBlank),
            releaseDate = info?.releaseDate?.takeIf(String::isNotBlank),
            durationSeconds = info?.durationSeconds?.toIntOrNull(),
            fetchedAt = Clock.System.now().toEpochMilliseconds(),
        )
    }
}
