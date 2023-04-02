package com.m3u.data.repository.impl

import com.m3u.core.annotation.FeedStrategy
import com.m3u.core.architecture.AbstractLogger
import com.m3u.core.architecture.Configuration
import com.m3u.core.architecture.Logger
import com.m3u.core.util.collection.belong
import com.m3u.core.wrapper.Resource
import com.m3u.core.wrapper.emitMessage
import com.m3u.core.wrapper.emitResource
import com.m3u.core.wrapper.resourceFlow
import com.m3u.data.local.dao.FeedDao
import com.m3u.data.local.dao.LiveDao
import com.m3u.data.local.entity.Feed
import com.m3u.data.local.entity.Live
import com.m3u.data.remote.parser.Parser
import com.m3u.data.remote.parser.execute
import com.m3u.data.remote.parser.impl.DefaultPlaylistParser
import com.m3u.data.remote.parser.model.toLive
import com.m3u.data.repository.FeedRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class FeedRepositoryImpl @Inject constructor(
    private val feedDao: FeedDao,
    private val liveDao: LiveDao,
    logger: Logger,
    private val configuration: Configuration
) : FeedRepository, AbstractLogger(logger) {
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
                FeedStrategy.SKIP_FAVORITE ->
                    (groupedLives.getValue(true)).map { it.url } + skippedUrls
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

    override fun observeAll(): Flow<List<Feed>> = sandbox {
        feedDao.observeAll()
    } ?: flow { }

    override fun observe(url: String): Flow<Feed?> = sandbox {
        feedDao.observeByUrl(url)
    } ?: flow { }

    override suspend fun get(url: String): Feed? = sandbox {
        feedDao.getByUrl(url)
    }

    override suspend fun unsubscribe(url: String): Feed? = sandbox {
        val feed = feedDao.getByUrl(url)
        liveDao.deleteByFeedUrl(url)
        feed?.also {
            feedDao.delete(it)
        }
    }


    private suspend fun parse(url: String): List<Live> {
        val parser = when {
            DefaultPlaylistParser.match(url) -> Parser.newM3UParser()
            else -> error("Unsupported url: $url")
        }
        return parser.run {
            execute(
                url = url,
                connectTimeout = configuration.connectTimeout
            )
                .map { it.toLive(url) }
        }
    }
}