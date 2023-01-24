package com.m3u.data.repository

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
    fun parseUrlToLocal(url: URL): Flow<Resource<Unit>>
    fun observeAllSubscriptions(): Flow<List<Subscription>>
}

class SubscriptionRepositoryImpl @Inject constructor(
    private val subscriptionDao: SubscriptionDao,
    private val liveDao: LiveDao
) : SubscriptionRepository {
    override fun parseUrlToLocal(url: URL): Flow<Resource<Unit>> = flow {
        emit(Resource.Loading)
        val host = url.host
        val parser = when {
            host.endsWith(".m3u", ignoreCase = true) -> Parser.newM3UParser()
            host.endsWith(".m3u8", ignoreCase = true) -> Parser.newM3UParser()
            else -> error("Cannot finger out which parser could to handle this url.")
        }
        try {
            parser.parse(url)
            val m3us = parser.get()
            val group = m3us.groupBy { it.group }
            group.keys.forEach {
                val subscription = Subscription(
                    title = it
                )
                subscriptionDao.insert(subscription)
            }
            val lives = m3us.map { it.toLive() }
            liveDao.insert(*lives.toTypedArray())
            emit(Resource.Success(Unit))
        } catch (e: Exception) {
            emit(Resource.Failure(e.message))
        }

    }

    override fun observeAllSubscriptions(): Flow<List<Subscription>> {
        return subscriptionDao.observeAll()
    }
}