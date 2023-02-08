package com.m3u.data.repository.impl

import com.m3u.core.architecture.Logger
import com.m3u.core.wrapper.Resource
import com.m3u.core.wrapper.emitMessage
import com.m3u.core.wrapper.emitResource
import com.m3u.core.wrapper.resourceFlow
import com.m3u.data.dao.LiveDao
import com.m3u.data.dao.SubscriptionDao
import com.m3u.data.entity.Subscription
import com.m3u.data.interceptor.LoggerInterceptor
import com.m3u.data.repository.SubscriptionRepository
import com.m3u.data.source.mather.m3u.M3UMatcher
import com.m3u.data.source.parser.Parser
import com.m3u.data.source.parser.m3u.M3U
import com.m3u.data.source.parser.m3u.toLive
import com.m3u.data.source.parser.parse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.net.URL
import javax.inject.Inject

class SubscriptionRepositoryImpl @Inject constructor(
    private val subscriptionDao: SubscriptionDao,
    private val liveDao: LiveDao,
    private val logger: Logger
) : SubscriptionRepository {
    private fun createParser(url: URL): Parser<List<M3U>, M3U>? = when {
        M3UMatcher.match(url) -> Parser.newM3UParser()
        else -> null
    }

    override fun subscribe(title: String, url: URL): Flow<Resource<Unit>> = resourceFlow {
        val parser = createParser(url) ?: run {
            emitMessage("Unsupported url: $url")
            return@resourceFlow
        }
        try {
            val result = parser.run {
                addInterceptor(LoggerInterceptor())
                parse(url)
                get()
            }
            val stringUrl = url.toString()
            val subscription = Subscription(title, stringUrl)
            subscriptionDao.insert(subscription)
            val lives = result.map { it.toLive(stringUrl) }
            liveDao.deleteBySubscriptionUrl(stringUrl)
            lives.forEach { liveDao.insert(it) }
            emitResource(Unit)
        } catch (e: Exception) {
            logger.log(e)
            emitMessage(e.message)
        }
    }

    override fun observeAll(): Flow<List<Subscription>> = try {
        subscriptionDao.observeAll()
    } catch (e: Exception) {
        logger.log(e)
        flow { }
    }


    override fun observe(url: String): Flow<Subscription?> = try {
        subscriptionDao.observeByUrl(url)
    } catch (e: Exception) {
        logger.log(e)
        flow { }
    }

    override suspend fun get(url: String): Subscription? = try {
        subscriptionDao.getByUrl(url)
    } catch (e: Exception) {
        logger.log(e)
        null
    }
}