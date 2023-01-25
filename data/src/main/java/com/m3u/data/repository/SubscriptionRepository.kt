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
    fun observeDetail(id: Int): Flow<Subscription?>
}

class SubscriptionRepositoryImpl @Inject constructor(
    private val subscriptionDao: SubscriptionDao,
    private val liveDao: LiveDao
) : SubscriptionRepository {
    override fun parseUrlToLocal(url: URL): Flow<Resource<Unit>> = flow {
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

            group.keys.forEach { subscriptionTitle ->
                val subscriptionWithoutId = Subscription(
                    title = subscriptionTitle,
                    url = path
                )
                val oldId = subscriptionDao.getByUrl(path)?.id
                val newId = subscriptionDao.insert(subscriptionWithoutId).toInt()
                oldId?.let { liveDao.deleteBySubscriptionId(it) }
                val subscription = subscriptionDao.getById(newId)
                if (subscription != null) {
                    val lives = m3us.map { it.toLive(subscription.id) }
                    lives.forEach { liveDao.insert(it) }
                    emit(Resource.Success(Unit))
                } else {
                    emit(Resource.Failure("Cannot save subscription"))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emit(Resource.Failure(e.message))
        }
    }

    override fun observeAllSubscriptions(): Flow<List<Subscription>> = subscriptionDao.observeAll()

    override fun observeDetail(id: Int): Flow<Subscription?> = subscriptionDao.observeById(id)

}