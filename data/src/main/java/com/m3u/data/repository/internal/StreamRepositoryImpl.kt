package com.m3u.data.repository.internal

import com.m3u.core.architecture.logger.Logger
import com.m3u.core.architecture.logger.execute
import com.m3u.core.architecture.logger.sandBox
import com.m3u.data.database.dao.StreamDao
import com.m3u.data.database.model.Stream
import com.m3u.data.repository.StreamRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.datetime.Clock
import javax.inject.Inject
import kotlin.time.Duration

internal class StreamRepositoryImpl @Inject constructor(
    private val streamDao: StreamDao,
    private val logger: Logger,
) : StreamRepository {
    override fun observe(id: Int): Flow<Stream?> = streamDao
        .observeById(id)
        .catch { emit(null) }

    override fun observeAll(): Flow<List<Stream>> = streamDao
        .observeAll()
        .catch { emit(emptyList()) }

    override suspend fun get(id: Int): Stream? = logger.execute {
        streamDao.get(id)
    }

    override suspend fun getByPlaylistUrl(playlistUrl: String): List<Stream> = logger.execute {
        streamDao.getByPlaylistUrl(playlistUrl)
    } ?: emptyList()

    @Deprecated("stream url is not unique")
    override suspend fun getByUrl(url: String): Stream? = logger.execute {
        streamDao.getByUrl(url)
    }

    override suspend fun setFavourite(id: Int, target: Boolean) = logger.sandBox {
        streamDao.setFavourite(id, target)
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
}
