package com.m3u.data.repository

import com.m3u.core.wrapper.Resource
import com.m3u.data.entity.Subscription
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.net.URL

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
