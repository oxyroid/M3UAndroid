package com.m3u.data.repository.provider

import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.security.CredentialHandle
import com.m3u.extension.api.subscription.ProviderKind
import com.m3u.extension.api.subscription.SubscriptionProviderDescriptor
import com.m3u.extension.api.subscription.SubscriptionRefreshReason
import kotlinx.coroutines.flow.Flow

interface SubscriptionProviderRepository {
    suspend fun discoverProviders(): List<DiscoveredSubscriptionProvider>
    fun observeAccountSummaries(): Flow<List<ProviderAccountSummary>>
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
    suspend fun invalidateUndecryptableCredentials(): Int
    suspend fun closeOrphanedPlaybackSessions(
        afterCreatedAtEpochMillis: Long? = null,
        afterSessionId: String? = null,
    ): ProviderSessionCleanupResult
}

data class DiscoveredSubscriptionProvider(
    val descriptor: SubscriptionProviderDescriptor,
    val executionKind: SubscriptionProviderExecutionKind,
)

enum class SubscriptionProviderExecutionKind {
    BUILT_IN,
    EXTERNAL,
}

data class ProviderAccountSummary(
    val playlistTitle: String,
    val playlistUrl: String,
    val providerId: ExtensionId,
    val providerKind: ProviderKind,
    val baseUrl: String,
    val username: String,
    val serverName: String,
    val requiresReauthentication: Boolean,
    val requiresExtensionOwnerConfirmation: Boolean = false,
)

class ProviderDiscoveryException(
    val failureCount: Int,
) : IllegalStateException("No subscription provider completed discovery") {
    init {
        require(failureCount > 0)
    }

    val code: String = "provider.discovery_failed"
}

data class ProviderSubscriptionRequest(
    val title: String,
    val providerId: ExtensionId,
    val providerKind: ProviderKind,
    val settingValues: Map<String, String> = emptyMap(),
    val credentialHandles: Map<String, CredentialHandle> = emptyMap(),
    /**
     * Host-selected account being explicitly reauthenticated. Plugins never control this value.
     * It also authorizes rebinding a restored account to the currently trusted plugin identity.
     */
    val reauthenticationPlaylistUrl: String? = null,
)

data class ProviderSubscriptionResult(
    val playlistUrl: String,
    val channelCount: Int,
)

data class ProviderSessionCleanupResult(
    val closedCount: Int,
    val pendingCount: Int,
    val recoverablePendingCount: Int,
    val continuationCreatedAtEpochMillis: Long? = null,
    val continuationSessionId: String? = null,
) {
    init {
        require(closedCount >= 0)
        require(pendingCount >= 0)
        require(recoverablePendingCount in 0..pendingCount)
        require(
            (continuationCreatedAtEpochMillis == null) ==
                (continuationSessionId == null)
        )
        require(continuationCreatedAtEpochMillis == null || continuationCreatedAtEpochMillis >= 0L)
        require(continuationSessionId == null || continuationSessionId.isNotBlank())
    }
}

data class ProviderPlaybackSource(
    val url: String,
    val headers: Map<String, String>,
    val session: ProviderPlaybackSession?,
    val allowCrossOriginRequests: Boolean,
)

data class ProviderPlaybackSession(
    val id: String,
    val accountId: String,
    val providerId: String,
    val itemId: String,
    val mediaSourceId: String?,
    val sourceType: String,
    val playSessionId: String?,
    val liveStreamId: String?,
) {
    init {
        require(itemId.isNotBlank() && itemId.encodeToByteArray().size <= 512)
        require(
            mediaSourceId == null ||
                mediaSourceId.isNotBlank() && mediaSourceId.encodeToByteArray().size <= 512
        )
        require(sourceType.isNotBlank() && sourceType.encodeToByteArray().size <= 128)
        require(playSessionId != null || liveStreamId != null)
        require(
            playSessionId == null ||
                playSessionId.isNotBlank() && playSessionId.encodeToByteArray().size <= 512
        )
        require(
            liveStreamId == null ||
                liveStreamId.isNotBlank() && liveStreamId.encodeToByteArray().size <= 512
        )
    }
}

enum class ProviderPlaybackCloseReason {
    STOPPED,
    ENDED,
    CHANNEL_CHANGED,
    PLAYBACK_FAILED,
    RECOVERY,
}

class ProviderOperationException(
    message: String,
    val code: String? = null,
    val recoverable: Boolean = false,
    val details: Map<String, String> = emptyMap(),
    cause: Throwable? = null,
) : IllegalStateException(message, cause)
