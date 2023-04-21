package com.m3u.data.repository.impl

import com.m3u.core.annotation.FeedStrategy
import com.m3u.core.architecture.configuration.Configuration
import com.m3u.core.architecture.logger.Logger
import com.m3u.core.architecture.logger.execute
import com.m3u.core.architecture.logger.sandBox
import com.m3u.core.util.collection.belong
import com.m3u.core.wrapper.Resource
import com.m3u.core.wrapper.emitMessage
import com.m3u.core.wrapper.emitResource
import com.m3u.core.wrapper.resourceFlow
import com.m3u.data.database.dao.FeedDao
import com.m3u.data.database.dao.LiveDao
import com.m3u.data.database.entity.Feed
import com.m3u.data.database.entity.Live
import com.m3u.data.remote.parser.execute
import com.m3u.data.remote.parser.m3u.PlaylistParser
import com.m3u.data.remote.parser.m3u.toLive
import com.m3u.data.repository.FeedRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FeedRepositoryImpl @Inject constructor(
    private val feedDao: FeedDao,
    private val liveDao: LiveDao,
    private val logger: Logger,
    private val configuration: Configuration,
    private val parser: PlaylistParser
) : FeedRepository {
    override fun subscribe(
        title: String,
        url: String,
        @FeedStrategy strategy: Int
    ): Flow<Resource<Unit>> = resourceFlow {
        try {
            val lives = parse(url)
            val feed = Feed(title, url)
            feedDao.insert(feed)
            val cachedLives = liveDao.getByFeedUrl(url)
            val skippedUrls = mutableListOf<String>()
            val groupedLives by lazy {
                cachedLives.groupBy { it.favourite }.withDefault { emptyList() }
            }
            val invalidateLives = when (strategy) {
                FeedStrategy.ALL -> cachedLives
                FeedStrategy.SKIP_FAVORITE -> groupedLives.getValue(false)
                else -> emptyList()
            }
            invalidateLives.forEach { live ->
                if (live belong lives) {
                    skippedUrls += live.url
                } else {
                    liveDao.deleteByUrl(live.url)
                }
            }
            val existedUrls = when (strategy) {
                FeedStrategy.ALL -> skippedUrls
                FeedStrategy.SKIP_FAVORITE -> groupedLives.getValue(true)
                    .map { it.url } + skippedUrls
                else -> emptyList()
            }
            lives
                .filterNot { it.url in existedUrls }
                .forEach {
                    liveDao.insert(it)
                }
            emitResource(Unit)
        } catch (e: Exception) {
            logger.log(e)
            emitMessage(e.message)
        }
    }

    override fun observeAll(): Flow<List<Feed>> = logger.execute {
        feedDao.observeAll()
    } ?: flow { }

    override fun observe(url: String): Flow<Feed?> = logger.execute {
        feedDao.observeByUrl(url)
    } ?: flow { }

    override suspend fun get(url: String): Feed? = logger.execute {
        feedDao.getByUrl(url)
    }

    override suspend fun unsubscribe(url: String): Feed? = logger.execute {
        val feed = feedDao.getByUrl(url)
        liveDao.deleteByFeedUrl(url)
        feed?.also {
            feedDao.delete(it)
        }
    }

    private suspend fun parse(url: String): List<Live> = parser.execute(
        url = url,
        connectTimeout = configuration.connectTimeout
    )
        .map { it.toLive(url) }

    override suspend fun rename(url: String, target: String) = logger.sandBox {
        feedDao.rename(url, target)
    }
}