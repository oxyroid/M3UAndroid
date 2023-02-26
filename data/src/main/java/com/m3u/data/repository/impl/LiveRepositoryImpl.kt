package com.m3u.data.repository.impl

import com.m3u.core.architecture.Configuration
import com.m3u.core.architecture.Logger
import com.m3u.core.architecture.AbstractLogger
import com.m3u.core.wrapper.Resource
import com.m3u.core.wrapper.emitMessage
import com.m3u.core.wrapper.emitResource
import com.m3u.core.wrapper.resourceFlow
import com.m3u.data.local.dao.LiveDao
import com.m3u.data.local.entity.Live
import com.m3u.data.repository.LiveRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class LiveRepositoryImpl @Inject constructor(
    private val liveDao: LiveDao,
    logger: Logger,
    private val configuration: Configuration
) : LiveRepository, AbstractLogger(logger) {
    override fun observe(id: Int): Flow<Live?> = sandbox {
        liveDao.observeById(id)
    } ?: flow { }

    override fun observeAll(): Flow<List<Live>> = sandbox {
        liveDao.observeAll()
    } ?: flow { }

    override suspend fun get(id: Int): Live? = sandbox {
        liveDao.get(id)
    }

    override suspend fun getByFeedUrl(feedUrl: String): List<Live> = sandbox {
        liveDao.getByFeedUrl(feedUrl)
    } ?: emptyList()

    override suspend fun getByUrl(url: String): Live? = sandbox {
        liveDao.getByUrl(url)
    }

    override suspend fun setFavourite(id: Int, target: Boolean) = sandbox {
        liveDao.setFavouriteLive(id, target)
    } ?: Unit

    override fun setMuteByUrl(url: String, target: Boolean): Flow<Resource<Unit>> = resourceFlow {
        try {
            val urls = configuration.mutedUrls
            if (target) {
                if (url !in urls) configuration.mutedUrls = (urls + url)
            } else {
                if (url in urls) configuration.mutedUrls = (urls - url)
            }
            emitResource(Unit)
        } catch (e: Exception) {
            logger.log(e)
            emitMessage(e.message)
        }
    }
}