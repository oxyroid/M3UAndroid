package com.m3u.extension.api.subscription

import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.ExtensionPayload

private val PROVIDER_KIND_PATTERN = Regex("[a-z0-9]+(?:[._-][a-z0-9]+)*")

@JvmInline
value class ProviderKind(val value: String) {
    init {
        require(value.matches(PROVIDER_KIND_PATTERN)) {
            "Provider kind must be a lowercase identifier"
        }
    }

    override fun toString(): String = value
}

object EmbyCompatibleProviderKinds {
    val Emby = ProviderKind("emby")
    val Jellyfin = ProviderKind("jellyfin")
    val Auto = ProviderKind("auto")
}

sealed interface ProviderAuthentication {
    data class UsernamePassword(
        val username: String,
        val password: String,
    ) : ProviderAuthentication
}

data class SubscriptionProviderDiscoverRequest(
    val localeTag: String? = null,
) : ExtensionPayload

data class SubscriptionProviderDescriptor(
    val providerId: ExtensionId,
    val displayName: String,
    val supportedKinds: Set<ProviderKind>,
)

data class SubscriptionProviderDiscoverResult(
    val providers: List<SubscriptionProviderDescriptor>,
) : ExtensionPayload

data class SubscriptionProviderValidateRequest(
    val baseUrl: String,
    val providerKind: ProviderKind,
    val authentication: ProviderAuthentication,
) : ExtensionPayload

data class ValidatedProviderAccount(
    val normalizedBaseUrl: String,
    val detectedKind: ProviderKind,
    val serverId: String,
    val serverName: String,
    val serverVersion: String,
    val userId: String,
    val username: String,
)

data class SubscriptionProviderValidateResult(
    val account: ValidatedProviderAccount,
    val accessToken: String,
) : ExtensionPayload

data class ProviderAccountReference(
    val accountId: String,
    val providerId: ExtensionId,
    val providerKind: ProviderKind,
    val baseUrl: String,
    val serverId: String,
    val serverName: String,
    val serverVersion: String,
    val userId: String,
    val username: String,
)

data class ProviderCredential(
    val accessToken: String,
)

enum class SubscriptionRefreshReason {
    INITIAL,
    MANUAL,
    BACKGROUND,
}

data class SubscriptionContentRefreshRequest(
    val account: ProviderAccountReference,
    val credential: ProviderCredential,
    val reason: SubscriptionRefreshReason,
    val lastSyncMetadata: Map<String, String> = emptyMap(),
) : ExtensionPayload

data class PlaybackReference(
    val providerId: ExtensionId,
    val itemId: String,
    val mediaSourceId: String? = null,
    val sourceType: String,
    val fallbackDirectUrl: String? = null,
)

data class SubscriptionChannelDescriptor(
    val remoteId: String,
    val title: String,
    val logoUrl: String? = null,
    val category: String,
    val playbackReference: PlaybackReference,
    val epgReference: String? = null,
    val metadata: Map<String, String> = emptyMap(),
)

data class SubscriptionSourceDescriptor(
    val remoteId: String,
    val title: String,
    val providerKind: ProviderKind,
)

data class SubscriptionContentRefreshResult(
    val source: SubscriptionSourceDescriptor,
    val channels: List<SubscriptionChannelDescriptor>,
    val syncMetadata: Map<String, String> = emptyMap(),
    val diagnostics: List<String> = emptyList(),
) : ExtensionPayload

data class PlaybackPreferences(
    val maxStreamingBitrate: Long? = null,
    val allowTranscoding: Boolean = true,
)

data class PlaybackSourceResolveRequest(
    val account: ProviderAccountReference,
    val credential: ProviderCredential,
    val reference: PlaybackReference,
    val preferences: PlaybackPreferences = PlaybackPreferences(),
) : ExtensionPayload

data class PlaybackSessionDescriptor(
    val playSessionId: String? = null,
    val liveStreamId: String? = null,
)

data class PlaybackSourceResolveResult(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val mediaSourceId: String? = null,
    val expiresAtEpochMilliseconds: Long? = null,
    val session: PlaybackSessionDescriptor? = null,
) : ExtensionPayload

enum class PlaybackSessionCloseReason {
    STOPPED,
    CHANNEL_CHANGED,
    PLAYBACK_FAILED,
}

data class PlaybackSessionCloseRequest(
    val account: ProviderAccountReference,
    val credential: ProviderCredential,
    val reference: PlaybackReference,
    val session: PlaybackSessionDescriptor,
    val reason: PlaybackSessionCloseReason,
) : ExtensionPayload

data class PlaybackSessionCloseResult(
    val closed: Boolean,
    val diagnostics: List<String> = emptyList(),
) : ExtensionPayload
