package com.m3u.data.repository.provider

import com.m3u.data.database.model.DataSource

interface SubscriptionProviderRepository {
    suspend fun subscribe(request: ProviderSubscriptionRequest): ProviderSubscriptionResult
    suspend fun refresh(playlistUrl: String): ProviderSubscriptionResult
    suspend fun resolvePlayback(channelId: Int): ProviderPlaybackSource?
    suspend fun closePlayback(
        session: ProviderPlaybackSession,
        reason: ProviderPlaybackCloseReason,
    ): Boolean

    suspend fun removeAccount(playlistUrl: String)
}

data class ProviderSubscriptionRequest(
    val title: String,
    val baseUrl: String,
    val username: String,
    val password: String,
    val source: DataSource,
)

data class ProviderSubscriptionResult(
    val playlistUrl: String,
    val channelCount: Int,
)

data class ProviderPlaybackSource(
    val url: String,
    val headers: Map<String, String>,
    val session: ProviderPlaybackSession?,
)

data class ProviderPlaybackSession(
    val accountId: String,
    val providerId: String,
    val itemId: String,
    val mediaSourceId: String?,
    val sourceType: String,
    val fallbackDirectUrl: String?,
    val playSessionId: String?,
    val liveStreamId: String?,
)

enum class ProviderPlaybackCloseReason {
    STOPPED,
    CHANNEL_CHANGED,
    PLAYBACK_FAILED,
}

class ProviderOperationException(message: String) : IllegalStateException(message)
