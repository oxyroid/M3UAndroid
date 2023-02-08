package com.m3u.data.repository

import com.m3u.core.wrapper.Resource
import com.m3u.core.wrapper.resourceChanelFlow
import com.m3u.core.wrapper.sendMessage
import com.m3u.data.entity.Subscription
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.net.URL

interface SubscriptionRepository {
    fun subscribe(title: String, url: URL): Flow<Resource<Unit>>
    fun observeAll(): Flow<List<Subscription>>
    fun observe(url: String): Flow<Subscription?>
    suspend fun get(url: String): Subscription?
}

fun SubscriptionRepository.sync(url: URL): Flow<Resource<Unit>> = resourceChanelFlow {
    val stringUrl = url.toString()
    val subscription = get(stringUrl)
    if (subscription == null) {
        sendMessage("Cannot find subscription: $stringUrl")
        return@resourceChanelFlow
    }
    subscribe(subscription.title, url)
        .onEach(::send)
        .launchIn(this)
}
