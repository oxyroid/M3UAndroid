package com.m3u.data.repository.channel

import android.content.Context
import androidx.paging.PagingSource
import com.m3u.core.architecture.logger.Logger
import com.m3u.core.architecture.logger.Profiles
import com.m3u.core.architecture.logger.execute
import com.m3u.core.architecture.logger.install
import com.m3u.core.architecture.logger.post
import com.m3u.core.architecture.logger.sandBox
import com.m3u.core.architecture.preferences.Preferences
import com.m3u.data.database.dao.ChannelDao
import com.m3u.data.database.dao.PlaylistDao
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.isSeries
import com.m3u.data.repository.channel.ChannelRepository.Sort
import com.m3u.extension.api.runner.ClientRunner
import com.m3u.extension.api.runner.CodeRunner
import com.m3u.extension.api.runner.EMediaRunner
import com.m3u.extension.runtime.ExtensionLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import javax.inject.Inject
import kotlin.time.Duration

internal class ChannelRepositoryImpl @Inject constructor(
    private val channelDao: ChannelDao,
    private val playlistDao: PlaylistDao,
    private val preferences: Preferences,
    logger: Logger,
    @ApplicationContext context: Context
) : ChannelRepository {
    private val logger = logger.install(Profiles.REPOS_CHANNEL)
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    init {
        coroutineScope.launch {
            val extensions = ExtensionLoader.loadExtensions(context)
            logger.install(Profiles.REPOS_CHANNEL).post { extensions.size }
            extensions.forEach {
                val runners = it.runners
                logger.install(Profiles.REPOS_CHANNEL).post { "runners: $runners" }
                runners.forEach { runner ->
                    when (runner) {
                        is ClientRunner -> {}
                        is CodeRunner -> {
                            runner.run()
                        }

                        is EMediaRunner -> {
                        }
                    }
                }
            }
        }
    }

    override fun observe(id: Int): Flow<Channel?> = channelDao
        .observeById(id)
        .catch { emit(null) }

    override fun observeAllByPlaylistUrl(playlistUrl: String): Flow<List<Channel>> = channelDao
        .observeAllByPlaylistUrl(playlistUrl)
        .catch { emit(emptyList()) }

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
    }

    override suspend fun get(id: Int): Channel? = logger.execute {
        channelDao.get(id)
    }

    override suspend fun getRandomIgnoreSeriesAndHidden(): Channel? = logger.execute {
        val playlists = playlistDao.getAll()
        val seriesPlaylistUrls = playlists
            .filter { it.isSeries }
            .map { it.url }
            .toTypedArray()
        if (!preferences.randomlyInFavourite) channelDao.randomIgnoreSeriesAndHidden(*seriesPlaylistUrls)
        else channelDao.randomIgnoreSeriesInFavourite(*seriesPlaylistUrls)
    }

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

    override fun observeAllUnseenFavourites(limit: Duration): Flow<List<Channel>> =
        channelDao.observeAllUnseenFavourites(
            limit = limit.inWholeMilliseconds,
            current = Clock.System.now().toEpochMilliseconds()
        )
            .catch { emit(emptyList()) }

    override fun observeAllFavourite(): Flow<List<Channel>> = channelDao.observeAllFavourite()
        .catch { emit(emptyList()) }

    override fun observeAllHidden(): Flow<List<Channel>> = channelDao.observeAllHidden()
        .catch { emit(emptyList()) }
}
