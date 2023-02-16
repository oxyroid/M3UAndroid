package com.m3u.data.repository.impl

import com.m3u.core.annotation.FeedStrategy
import com.m3u.core.architecture.Logger
import com.m3u.core.util.collection.belong
import com.m3u.core.wrapper.Resource
import com.m3u.core.wrapper.emitMessage
import com.m3u.core.wrapper.emitResource
import com.m3u.core.wrapper.resourceFlow
import com.m3u.data.dao.FeedDao
import com.m3u.data.dao.LiveDao
import com.m3u.data.entity.Feed
import com.m3u.data.interceptor.LoggerInterceptor
import com.m3u.data.repository.FeedRepository
import com.m3u.data.source.analyzer.Analyzer
import com.m3u.data.source.analyzer.analyze
import com.m3u.data.source.matcher.m3u.M3UMatcher
import com.m3u.data.source.model.toLive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class FeedRepositoryImpl @Inject constructor(
    private val feedDao: FeedDao,
    private val liveDao: LiveDao,
    private val logger: Logger
) : FeedRepository {
    private fun createParser(url: String): Parser<List<M3U>, M3U> = when {
        M3UMatcher.match(url) -> Parser.newM3UParser()
        else -> error("Unsupported url: $url")
    }

    override fun subscribe(
        title: String,
        url: String,
        @FeedStrategy strategy: Int
    ): Flow<Resource<Unit>> = resourceFlow {
        try {
            val lives = analyze(url)
            val feed = Feed(title, url)
            feedDao.insert(feed)
            val lives = result.map { it.toLive(url) }

            when (strategy) {
                FeedStrategy.ALL -> {
                    liveDao.deleteByFeedUrl(url)
                    lives.forEach { liveDao.insert(it) }
                }
                FeedStrategy.SKIP_FAVORITE -> {
                    val cachedLives = liveDao.getByFeedUrl(url)
                    val groupedLives = cachedLives.groupBy { it.favourite }

                    val favouriteLives = groupedLives[true] ?: emptyList()
                    val favouriteUrls = favouriteLives.map { it.url }

                    val invalidateLives = groupedLives[false] ?: emptyList()

                    val skippedUrls = mutableListOf<String>()

                    invalidateLives.forEach { live ->
                        if (live belong lives) {
                            skippedUrls += live.url
                        } else {
                            liveDao.deleteByUrl(live.url)
                        }
                    }

                    lives
                        .filterNot { it.url in (favouriteUrls + skippedUrls) }
                        .forEach {
                            liveDao.insert(it)
                        }
                }
            }
            emitResource(Unit)
        } catch (e: Exception) {
            logger.log(e)
            emitMessage(e.message)
        }
    }

    override fun observeAll(): Flow<List<Feed>> = try {
        feedDao.observeAll()
    } catch (e: Exception) {
        logger.log(e)
        flow { }
    }


    override fun observe(url: String): Flow<Feed?> = try {
        feedDao.observeByUrl(url)
    } catch (e: Exception) {
        logger.log(e)
        flow { }
    }

    override suspend fun get(url: String): Feed? = try {
        feedDao.getByUrl(url)
    }

    private suspend fun analyze(url: String): List<Live> {
        val analyzer = when {
            M3UMatcher.match(url) -> Analyzer.newM3UParser()
            else -> error("Unsupported url: $url")
        }
        return analyzer.run {
            analyze(
                url = url,
                connectTimeout = configuration.connectTimeout
            )
            get().map { it.toLive(url) }
        }
    }
}