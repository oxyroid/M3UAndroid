package com.m3u.extension.api.subscription

import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.ExtensionHookIds
import com.m3u.extension.api.ExtensionPayload
import com.m3u.extension.api.ExtensionSettingSchema
import com.m3u.extension.api.HookSpec
import com.m3u.extension.api.security.CredentialHandle
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

private val PROVIDER_KIND_PATTERN = Regex("[a-z0-9]+(?:[._-][a-z0-9]+)*")

@Serializable
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

object SubscriptionProviderSettingKeys {
    const val BaseUrl = "base_url"
    const val Username = "username"
    const val Password = "password"
}

@Serializable
sealed interface ProviderAuthentication {
    @Serializable
    data class UsernamePassword(
        val username: String,
        val password: CredentialHandle,
    ) : ProviderAuthentication
}

@Serializable
data class SubscriptionProviderDiscoverRequest(
    val localeTag: String? = null,
) : ExtensionPayload

@Serializable
data class SubscriptionProviderDescriptor(
    val providerId: ExtensionId,
    val displayName: String,
    val supportedKinds: Set<ProviderKind>,
    val settingsSchema: ExtensionSettingSchema? = null,
)

@Serializable
data class SubscriptionProviderDiscoverResult(
    val providers: List<SubscriptionProviderDescriptor>,
) : ExtensionPayload

@Serializable
data class SubscriptionProviderValidateRequest(
    val providerKind: ProviderKind,
    val settingValues: Map<String, String> = emptyMap(),
    val credentialHandles: Map<String, CredentialHandle> = emptyMap(),
) : ExtensionPayload

@Serializable
data class ValidatedProviderAccount(
    val normalizedBaseUrl: String,
    val detectedKind: ProviderKind,
    val serverId: String,
    val serverName: String,
    val serverVersion: String,
    val userId: String,
    val username: String,
)

@Serializable
data class SubscriptionProviderValidateResult(
    val account: ValidatedProviderAccount,
    val credential: CredentialHandle,
) : ExtensionPayload

@Serializable
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

@Serializable
data class ProviderCredential(
    val handle: CredentialHandle,
)

@Serializable
@JvmInline
value class SubscriptionRefreshReason(val value: String) {
    init {
        require(value.matches(Regex("[a-z][a-z0-9._-]*"))) { "Invalid refresh reason: $value" }
    }

    companion object {
        val Initial = SubscriptionRefreshReason("initial")
        val Manual = SubscriptionRefreshReason("manual")
        val Background = SubscriptionRefreshReason("background")
    }
}

@Serializable
data class SubscriptionContentRefreshRequest(
    val account: ProviderAccountReference,
    val credential: ProviderCredential,
    val reason: SubscriptionRefreshReason,
    val lastSyncMetadata: Map<String, String> = emptyMap(),
) : ExtensionPayload

@Serializable
data class PlaybackReference(
    val providerId: ExtensionId,
    val itemId: String,
    val mediaSourceId: String? = null,
    val sourceType: String,
    val fallbackDirectUrl: String? = null,
)

@Serializable
data class SubscriptionChannelDescriptor(
    val remoteId: String,
    val title: String,
    val logoUrl: String? = null,
    val category: String,
    val playbackReference: PlaybackReference,
    val epgReference: String? = null,
    val metadata: Map<String, String> = emptyMap(),
)

@Serializable
data class SubscriptionSourceDescriptor(
    val remoteId: String,
    val title: String,
    val providerKind: ProviderKind,
)

@Serializable
data class SubscriptionContentRefreshResult(
    val source: SubscriptionSourceDescriptor,
    val channels: List<SubscriptionChannelDescriptor>,
    val syncMetadata: Map<String, String> = emptyMap(),
    val diagnostics: List<String> = emptyList(),
) : ExtensionPayload

@Serializable
data class PlaybackPreferences(
    val maxStreamingBitrate: Long? = null,
    val allowTranscoding: Boolean = true,
)

@Serializable
data class PlaybackSourceResolveRequest(
    val account: ProviderAccountReference,
    val credential: ProviderCredential,
    val reference: PlaybackReference,
    val preferences: PlaybackPreferences = PlaybackPreferences(),
) : ExtensionPayload

@Serializable
data class PlaybackSessionDescriptor(
    val playSessionId: String? = null,
    val liveStreamId: String? = null,
)

@Serializable
data class PlaybackSourceResolveResult(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val mediaSourceId: String? = null,
    val expiresAtEpochMilliseconds: Long? = null,
    val session: PlaybackSessionDescriptor? = null,
) : ExtensionPayload

@Serializable
@JvmInline
value class PlaybackSessionCloseReason(val value: String) {
    init {
        require(value.matches(Regex("[a-z][a-z0-9._-]*"))) { "Invalid close reason: $value" }
    }

    companion object {
        val Stopped = PlaybackSessionCloseReason("stopped")
        val ChannelChanged = PlaybackSessionCloseReason("channel_changed")
        val PlaybackFailed = PlaybackSessionCloseReason("playback_failed")
        val Ended = PlaybackSessionCloseReason("ended")
        val Recovery = PlaybackSessionCloseReason("recovery")
    }
}

@Serializable
data class PlaybackSessionCloseRequest(
    val account: ProviderAccountReference,
    val credential: ProviderCredential,
    val reference: PlaybackReference,
    val session: PlaybackSessionDescriptor,
    val reason: PlaybackSessionCloseReason,
) : ExtensionPayload

@Serializable
data class PlaybackSessionCloseResult(
    val closed: Boolean,
    val diagnostics: List<String> = emptyList(),
) : ExtensionPayload

object SubscriptionHookSpecs {
    val Discover = HookSpec(
        hook = ExtensionHookIds.SubscriptionProviderDiscover,
        schemaVersion = 1,
        requestSerializer = SubscriptionProviderDiscoverRequest.serializer(),
        responseSerializer = SubscriptionProviderDiscoverResult.serializer(),
    )
    val Validate = HookSpec(
        hook = ExtensionHookIds.SubscriptionProviderValidate,
        schemaVersion = 1,
        requestSerializer = SubscriptionProviderValidateRequest.serializer(),
        responseSerializer = SubscriptionProviderValidateResult.serializer(),
    )
    val Refresh = HookSpec(
        hook = ExtensionHookIds.SubscriptionContentRefresh,
        schemaVersion = 1,
        requestSerializer = SubscriptionContentRefreshRequest.serializer(),
        responseSerializer = SubscriptionContentRefreshResult.serializer(),
    )
    val ResolvePlayback = HookSpec(
        hook = ExtensionHookIds.PlaybackSourceResolve,
        schemaVersion = 1,
        requestSerializer = PlaybackSourceResolveRequest.serializer(),
        responseSerializer = PlaybackSourceResolveResult.serializer(),
    )
    val ClosePlayback = HookSpec(
        hook = ExtensionHookIds.PlaybackSessionClose,
        schemaVersion = 1,
        requestSerializer = PlaybackSessionCloseRequest.serializer(),
        responseSerializer = PlaybackSessionCloseResult.serializer(),
    )
}
