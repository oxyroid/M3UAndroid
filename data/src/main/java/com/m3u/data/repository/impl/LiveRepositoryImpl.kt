package com.m3u.data.repository.impl

import com.m3u.core.architecture.Configuration
import com.m3u.core.architecture.Logger
import com.m3u.data.dao.LiveDao
import com.m3u.data.entity.Live
import com.m3u.data.repository.LiveRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class LiveRepositoryImpl @Inject constructor(
    private val liveDao: LiveDao,
    private val logger: Logger,
    private val configuration: Configuration
) : LiveRepository {
    override fun observe(id: Int): Flow<Live?> = try {
        liveDao.observeById(id)
    } catch (e: Exception) {
        logger.log(e)
        flow {}
    }

    override fun observeAll(): Flow<List<Live>> = try {
        liveDao.observeAll()
    } catch (e: Exception) {
        logger.log(e)
        flow {}
    }

    override suspend fun get(id: Int): Live? = try {
        liveDao.get(id)
    } catch (e: Exception) {
        logger.log(e)
        null
    }

    override suspend fun getByFeedUrl(feedUrl: String): List<Live> = try {
        liveDao.getByFeedId(feedUrl)
    } catch (e: Exception) {
        logger.log(e)
        emptyList()
    }

    override suspend fun getByUrl(url: String): Live? = try {
        liveDao.getByUrl(url)
    } catch (e: Exception) {
        logger.log(e)
        null
    }

    override suspend fun setFavourite(id: Int, target: Boolean) = try {
        liveDao.setFavouriteLive(id, target)
    } catch (e: Exception) {
        logger.log(e)
    }

    override suspend fun muteByUrl(url: String) {
        try {
            val urls = configuration.mutedUrls
            if (url !in urls) {
                configuration.mutedUrls = (urls + url)
            }
        } catch (e: Exception) {
            logger.log(e)
        }
    }
}