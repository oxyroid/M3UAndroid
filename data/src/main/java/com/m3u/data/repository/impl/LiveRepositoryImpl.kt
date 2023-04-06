package com.m3u.data.repository.impl

import com.m3u.core.architecture.logger.FileLoggerImpl
import com.m3u.core.architecture.logger.Logger
import com.m3u.core.architecture.logger.execute
import com.m3u.core.architecture.logger.sandBox
import com.m3u.data.database.dao.LiveDao
import com.m3u.data.database.entity.Live
import com.m3u.data.repository.LiveRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class LiveRepositoryImpl @Inject constructor(
    private val liveDao: LiveDao,
    @FileLoggerImpl private val logger: Logger
) : LiveRepository {
    override fun observe(id: Int): Flow<Live?> = logger.execute {
        liveDao.observeById(id)
    } ?: flow { }

    override fun observeAll(): Flow<List<Live>> = logger.execute {
        liveDao.observeAll()
    } ?: flow { }

    override suspend fun get(id: Int): Live? = logger.execute {
        liveDao.get(id)
    }

    override suspend fun getByFeedUrl(feedUrl: String): List<Live> = logger.execute {
        liveDao.getByFeedUrl(feedUrl)
    } ?: emptyList()

    override suspend fun getByUrl(url: String): Live? = logger.execute {
        liveDao.getByUrl(url)
    }

    override suspend fun setFavourite(id: Int, target: Boolean) = logger.sandBox {
        liveDao.setFavourite(id, target)
    }

    override suspend fun setBanned(id: Int, target: Boolean) {
        logger.execute {
            liveDao.setBanned(id, target)
        }
    }
}