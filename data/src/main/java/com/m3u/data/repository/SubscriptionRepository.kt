package com.m3u.data.repository

import android.util.Log
import com.m3u.core.wrapper.Resource
import com.m3u.data.dao.LiveDao
import com.m3u.data.dao.SubscriptionDao
import com.m3u.data.entity.Subscription
import com.m3u.data.model.toLive
import com.m3u.data.parser.Parser
import com.m3u.data.parser.parse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.net.URL
import javax.inject.Inject

private const val TAG = "SubscriptionRepository"

interface SubscriptionRepository {
    fun subscribe(
        title: String,
        url: URL
    ): Flow<Resource<Unit>>

    fun syncLatestSubscription(url: URL): Flow<Resource<Unit>>

    fun observeAllSubscriptions(): Flow<List<Subscription>>
    fun observeDetail(url: String): Flow<Subscription?>
    suspend fun getDetail(url: String): Subscription?
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
            val urlString = url.toString()
            val subscription = Subscription(
                title = title,
                url = urlString
            )
            subscriptionDao.insert(subscription)

            val lives = m3us.map { it.toLive(urlString) }
            liveDao.deleteBySubscriptionUrl(urlString)
            lives.forEach { liveDao.insert(it) }
            emit(Resource.Success(Unit))
        } catch (e: Exception) {
            Log.e(TAG, "subscribe: ", e)
            emit(Resource.Failure(e.message))
        }
    }


    override fun syncLatestSubscription(url: URL): Flow<Resource<Unit>> = channelFlow {
        val urlString = url.toString()
        val subscription = subscriptionDao.getByUrl(urlString)
        if (subscription == null) {
            send(Resource.Failure("Cannot find subscription: $url"))
            return@channelFlow
        }
        subscribe(subscription.title, url)
            .onEach(::send)
            .launchIn(this)
    }

    override fun observeAllSubscriptions(): Flow<List<Subscription>> = try {
        subscriptionDao.observeAll()
    } catch (e: Exception) {
        Log.e(TAG, "observeAllSubscriptions: ", e)
        flow { }
    }


    override fun observeDetail(url: String): Flow<Subscription?> = try {
        subscriptionDao.observeByUrl(url)
    } catch (e: Exception) {
        Log.e(TAG, "observeDetail: ", e)
        flow { }
    }

    override suspend fun getDetail(url: String): Subscription? = try {
        subscriptionDao.getByUrl(url)
    } catch (e: Exception) {
        Log.e(TAG, "getDetail: ", e)
        null
    }
}