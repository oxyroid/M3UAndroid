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
import kotlinx.coroutines.flow.flow
import java.net.URL
import javax.inject.Inject

interface SubscriptionRepository {
    fun subscribe(url: URL): Flow<Resource<Unit>>
    fun observeAllSubscriptions(): Flow<List<Subscription>>
    fun observeDetail(url: String): Flow<Subscription?>
}

class SubscriptionRepositoryImpl @Inject constructor(
    private val subscriptionDao: SubscriptionDao,
    private val liveDao: LiveDao
) : SubscriptionRepository {

    private val TAG = "SubscriptionRepositoryImpl"

    override fun subscribe(url: URL): Flow<Resource<Unit>> = flow {
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
            val group = m3us.groupBy { it.group }

            val urlString = url.toString()
            group.keys.forEach { subscriptionTitle ->
                val subscription = Subscription(
                    title = subscriptionTitle,
                    url = urlString
                )
                subscriptionDao.delete(subscription)
                subscriptionDao.insert(subscription)

                val lives = m3us.map { it.toLive(urlString) }
                liveDao.deleteBySubscriptionUrl(urlString)
                lives.forEach { liveDao.insert(it) }
                emit(Resource.Success(Unit))
            }
        } catch (e: Exception) {
            Log.e(TAG, "parseUrlToLocal: ", e)
            emit(Resource.Failure(e.message))
        }
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

}