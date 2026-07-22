package com.m3u.data.repository.provider

import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.security.CredentialHandle
import com.m3u.extension.api.subscription.ProviderKind
import com.m3u.extension.api.subscription.SubscriptionProviderDescriptor
import com.m3u.extension.api.subscription.SubscriptionRefreshReason

interface SubscriptionProviderRepository {
    suspend fun discoverProviders(): List<SubscriptionProviderDescriptor>
    fun stageCredential(secret: String): CredentialHandle
    suspend fun subscribe(request: ProviderSubscriptionRequest): ProviderSubscriptionResult
    suspend fun refresh(
        playlistUrl: String,
        reason: SubscriptionRefreshReason = SubscriptionRefreshReason.Manual,
    ): ProviderSubscriptionResult
    suspend fun resolvePlayback(channelId: Int): ProviderPlaybackSource?
    suspend fun closePlayback(
        session: ProviderPlaybackSession,
        reason: ProviderPlaybackCloseReason,
    ): Boolean

    suspend fun removeAccount(playlistUrl: String)
    suspend fun closeOrphanedPlaybackSessions(): Int
}

data class ProviderSubscriptionRequest(
    val title: String,
    val providerId: ExtensionId,
    val providerKind: ProviderKind,
    val settingValues: Map<String, String> = emptyMap(),
    val credentialHandles: Map<String, CredentialHandle> = emptyMap(),
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
    val id: String,
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
