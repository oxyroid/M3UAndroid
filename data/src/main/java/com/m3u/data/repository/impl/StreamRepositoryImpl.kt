package com.m3u.data.repository.impl

import com.m3u.core.architecture.Logger
import com.m3u.core.architecture.execute
import com.m3u.core.architecture.sandBox
import com.m3u.data.database.dao.StreamDao
import com.m3u.data.database.entity.Stream
import com.m3u.data.repository.StreamRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class StreamRepositoryImpl @Inject constructor(
    private val streamDao: StreamDao,
    private val logger: Logger
) : StreamRepository {
    override fun observe(id: Int): Flow<Stream?> = logger.execute {
        streamDao.observeById(id)
    } ?: flow { }

    override fun observeAll(): Flow<List<Stream>> = logger.execute {
        streamDao.observeAll()
    } ?: flow { }

    override suspend fun get(id: Int): Stream? = logger.execute {
        streamDao.get(id)
    }

    override suspend fun getByPlaylistUrl(playlistUrl: String): List<Stream> = logger.execute {
        streamDao.getByPlaylistUrl(playlistUrl)
    } ?: emptyList()

    override suspend fun getByUrl(url: String): Stream? = logger.execute {
        streamDao.getByUrl(url)
    }

    override suspend fun setFavourite(id: Int, target: Boolean) = logger.sandBox {
        streamDao.setFavourite(id, target)
    }

    override suspend fun setBanned(id: Int, target: Boolean) {
        logger.execute {
            streamDao.setBanned(id, target)
        }
    }
}
