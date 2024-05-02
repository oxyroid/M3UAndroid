package com.m3u.data.repository.stream

import androidx.paging.PagingSource
import com.m3u.core.architecture.logger.Logger
import com.m3u.core.architecture.logger.Profiles
import com.m3u.core.architecture.logger.execute
import com.m3u.core.architecture.logger.install
import com.m3u.core.architecture.logger.sandBox
import com.m3u.core.architecture.preferences.Preferences
import com.m3u.data.database.dao.StreamDao
import com.m3u.data.database.model.Stream
import com.m3u.data.repository.stream.StreamRepository.Sort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.datetime.Clock
import javax.inject.Inject
import kotlin.time.Duration

internal class StreamRepositoryImpl @Inject constructor(
    private val streamDao: StreamDao,
    private val preferences: Preferences,
    logger: Logger,
) : StreamRepository {
    private val logger = logger.install(Profiles.REPOS_STREAM)
    override fun observe(id: Int): Flow<Stream?> = streamDao
        .observeById(id)
        .catch { emit(null) }

    override fun observeAllByPlaylistUrl(playlistUrl: String): Flow<List<Stream>> = streamDao
        .observeAllByPlaylistUrl(playlistUrl)
        .catch { emit(emptyList()) }

    override fun pagingAllByPlaylistUrl(
        url: String,
        query: String,
        sort: Sort
    ): PagingSource<Int, Stream> = when (sort) {
        Sort.UNSPECIFIED -> streamDao.pagingAllByPlaylistUrl(url, query)
        Sort.ASC -> streamDao.pagingAllByPlaylistUrlAsc(url, query)
        Sort.DESC -> streamDao.pagingAllByPlaylistUrlDesc(url, query)
        Sort.RECENTLY -> streamDao.pagingAllByPlaylistUrlRecently(url, query)
    }

    override suspend fun get(id: Int): Stream? = logger.execute {
        streamDao.get(id)
    }

    override suspend fun random(): Stream? = logger.execute {
        if (!preferences.randomlyInFavourite) streamDao.random()
        else streamDao.randomInFavourite()
    }

    override suspend fun getByPlaylistUrl(playlistUrl: String): List<Stream> = logger.execute {
        streamDao.getByPlaylistUrl(playlistUrl)
    } ?: emptyList()

    @Deprecated("stream url is not unique")
    override suspend fun getByUrl(url: String): Stream? = logger.execute {
        streamDao.getByUrl(url)
    }

    override suspend fun favouriteOrUnfavourite(id: Int) = logger.sandBox {
        val current = streamDao.get(id)?.favourite ?: return@sandBox
        streamDao.favouriteOrUnfavourite(id, !current)
    }

    override suspend fun hide(id: Int, target: Boolean) = logger.sandBox {
        streamDao.hide(id, target)
    }

    override suspend fun reportPlayed(id: Int) = logger.sandBox {
        val current = Clock.System.now().toEpochMilliseconds()
        streamDao.updateSeen(id, current)
    }

    override suspend fun getPlayedRecently(): Stream? = logger.execute {
        streamDao.getPlayedRecently()
    }

    override fun observeAllUnseenFavourites(limit: Duration): Flow<List<Stream>> =
        streamDao.observeAllUnseenFavourites(
            limit = limit.inWholeMilliseconds,
            current = Clock.System.now().toEpochMilliseconds()
        )
            .catch { emit(emptyList()) }

    override fun observeAllFavourite(): Flow<List<Stream>> = streamDao.observeAllFavourite()
        .catch { emit(emptyList()) }

    override fun observeAllHidden(): Flow<List<Stream>> = streamDao.observeAllHidden()
        .catch { emit(emptyList()) }
}
