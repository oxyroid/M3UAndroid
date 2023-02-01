package com.m3u.data.repository

import com.m3u.core.wrapper.Resource
import com.m3u.data.dao.LiveDao
import com.m3u.data.dao.SubscriptionDao
import com.m3u.data.entity.Subscription
import com.m3u.data.parser.m3u.toLive
import com.m3u.data.parser.Parser
import com.m3u.data.parser.parse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.net.URL
import javax.inject.Inject

interface SubscriptionRepository {
    fun subscribe(title: String, url: URL): Flow<Resource<Unit>>
    fun observeAll(): Flow<List<Subscription>>
    fun observe(url: String): Flow<Subscription?>
    suspend fun get(url: String): Subscription?
}

fun SubscriptionRepository.sync(url: URL): Flow<Resource<Unit>> = channelFlow {
    val stringUrl = url.toString()
    val subscription = get(stringUrl)
    if (subscription == null) {
        send(Resource.Failure("Cannot find subscription: $stringUrl"))
        return@channelFlow
    }
    subscribe(subscription.title, url)
        .onEach(::send)
        .launchIn(this)
}

class SubscriptionRepositoryImpl @Inject constructor(
    private val subscriptionDao: SubscriptionDao,
    private val liveDao: LiveDao
) : SubscriptionRepository {
    override fun subscribe(title: String, url: URL): Flow<Resource<Unit>> = flow {
        emit(Resource.Loading)
        val path = url.path
        val parser = when {
            path.endsWith(".m3u", ignoreCase = true) -> Parser.newM3UParser()
            path.endsWith(".m3u8", ignoreCase = true) -> Parser.newM3UParser()
            else -> {
                emit(Resource.Failure("Unsupported url: $url"))
                return@flow
            }
        }
        try {
            parser.parse(url)
            val m3us = parser.get()
            val stringUrl = url.toString()
            val subscription = Subscription(
                title = title,
                url = stringUrl
            )
            subscriptionDao.insert(subscription)

            val lives = m3us.map { it.toLive(stringUrl) }
            liveDao.deleteBySubscriptionUrl(stringUrl)
            lives.forEach { liveDao.insert(it) }
            emit(Resource.Success(Unit))
        } catch (e: Exception) {
            emit(Resource.Failure(e.message))
        }
    }

    override fun observeAll(): Flow<List<Subscription>> = try {
        subscriptionDao.observeAll()
    } catch (e: Exception) {
        flow { }
    }


    override fun observe(url: String): Flow<Subscription?> = try {
        subscriptionDao.observeByUrl(url)
    } catch (e: Exception) {
        flow { }
    }

    override suspend fun get(url: String): Subscription? = try {
        subscriptionDao.getByUrl(url)
    } catch (e: Exception) {
        null
    }
}