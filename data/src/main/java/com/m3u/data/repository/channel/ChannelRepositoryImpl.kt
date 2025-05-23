package com.m3u.data.repository.channel

import androidx.paging.PagingSource
import com.m3u.core.architecture.logger.Logger
import com.m3u.core.architecture.logger.Profiles
import com.m3u.core.architecture.logger.execute
import com.m3u.core.architecture.logger.install
import com.m3u.core.architecture.logger.sandBox
import com.m3u.core.architecture.preferences.Settings
import com.m3u.core.wrapper.Sort
import com.m3u.data.database.dao.ChannelDao
import com.m3u.data.database.dao.PlaylistDao
import com.m3u.data.database.model.AdjacentChannels
import com.m3u.data.database.model.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.datetime.Clock
import javax.inject.Inject
import kotlin.time.Duration

internal class ChannelRepositoryImpl @Inject constructor(
    private val channelDao: ChannelDao,
    private val playlistDao: PlaylistDao,
    private val settings: Settings,
    logger: Logger,
) : ChannelRepository {
    private val logger = logger.install(Profiles.REPOS_CHANNEL)
    override fun observe(id: Int): Flow<Channel?> = channelDao
        .observeById(id)
        .catch { emit(null) }

    override fun observeAllByPlaylistUrl(playlistUrl: String): Flow<List<Channel>> = channelDao
        .observeAllByPlaylistUrl(playlistUrl)
        .catch { emit(emptyList()) }

    override fun pagingAll(query: String): PagingSource<Int, Channel> {
        return channelDao.pagingAll(query)
    }

    override fun pagingAllByPlaylistUrl(
        url: String,
        category: String,
        query: String,
        sort: Sort
    ): PagingSource<Int, Channel> = when (sort) {
        Sort.UNSPECIFIED -> channelDao.pagingAllByPlaylistUrl(url, category, query)
        Sort.ASC -> channelDao.pagingAllByPlaylistUrlAsc(url, category, query)
        Sort.DESC -> channelDao.pagingAllByPlaylistUrlDesc(url, category, query)
        Sort.RECENTLY -> channelDao.pagingAllByPlaylistUrlRecently(url, category, query)
        Sort.MIXED -> channelDao.pagingAllByPlaylistUrlMixed(url, query)
    }

    override suspend fun get(id: Int): Channel? = logger.execute {
        channelDao.get(id)
    }

    override fun observeAdjacentChannels(
        channelId: Int,
        playlistUrl: String,
        category: String,
    ): Flow<AdjacentChannels> = channelDao.observeAdjacentChannels(
        channelId = channelId,
        playlistUrl = playlistUrl,
        category = category
    )

    override suspend fun getByPlaylistUrl(playlistUrl: String): List<Channel> = logger.execute {
        channelDao.getByPlaylistUrl(playlistUrl)
    } ?: emptyList()

    override suspend fun favouriteOrUnfavourite(id: Int) = logger.sandBox {
        val current = channelDao.get(id)?.favourite ?: return@sandBox
        channelDao.favouriteOrUnfavourite(id, !current)
    }

    override suspend fun hide(id: Int, target: Boolean) = logger.sandBox {
        channelDao.hide(id, target)
    }

    override suspend fun reportPlayed(id: Int) = logger.sandBox {
        val current = Clock.System.now().toEpochMilliseconds()
        channelDao.updateSeen(id, current)
    }

    override suspend fun getPlayedRecently(): Channel? = logger.execute {
        channelDao.getPlayedRecently()
    }

    override fun observePlayedRecently(): Flow<Channel?> = channelDao.observePlayedRecently()

    override fun observeAllUnseenFavorites(limit: Duration): Flow<List<Channel>> =
        channelDao.observeAllUnseenFavorites(
            limit = limit.inWholeMilliseconds,
            current = Clock.System.now().toEpochMilliseconds()
        )
            .catch { emit(emptyList()) }

    override fun observeAllFavorite(): Flow<List<Channel>> = channelDao.observeAllFavorite()
        .catch { emit(emptyList()) }

    override fun pagingAllFavorite(sort: Sort): PagingSource<Int, Channel> {
        return when (sort) {
            Sort.ASC -> channelDao.pagingAllFavoriteAsc()
            Sort.DESC -> channelDao.pagingAllFavoriteDesc()
            Sort.RECENTLY -> channelDao.pagingAllFavoriteRecently()
            else -> channelDao.pagingAllFavorite()
        }
    }

    override fun observeAllHidden(): Flow<List<Channel>> = channelDao.observeAllHidden()
        .catch { emit(emptyList()) }

    override fun search(query: String): PagingSource<Int, Channel> {
        return channelDao.query(query)
    }
}
