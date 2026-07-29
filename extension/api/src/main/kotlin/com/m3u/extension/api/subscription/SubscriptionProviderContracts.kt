package com.m3u.extension.api.subscription

import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.ExtensionErrorCode
import com.m3u.extension.api.ExtensionHookIds
import com.m3u.extension.api.ExtensionPayload
import com.m3u.extension.api.ExtensionSettingSchema
import com.m3u.extension.api.HookSpec
import com.m3u.extension.api.security.BrokerValue
import com.m3u.extension.api.security.CredentialHandle
import com.m3u.extension.api.security.ProviderAuthenticationReceipt
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

private val PROVIDER_KIND_PATTERN = Regex("[a-z0-9]+(?:[._-][a-z0-9]+)*")
private val PROVIDER_MEDIA_KIND_PATTERN = Regex("[a-z0-9]+(?:[._-][a-z0-9]+)*")
private val PLAYBACK_METHOD_PATTERN = Regex("[a-z0-9]+(?:[._-][a-z0-9]+)*")
private val PLAYBACK_SESSION_EVENT_PATTERN = Regex("[a-z0-9]+(?:[._-][a-z0-9]+)*")

@Serializable
@JvmInline
value class ProviderKind(val value: String) {
    init {
        require(
            value.length <= MAX_LENGTH &&
                value.matches(PROVIDER_KIND_PATTERN)
        ) {
            "Provider kind must be a lowercase identifier"
        }
    }

    override fun toString(): String = value

    companion object {
        const val MAX_LENGTH = 64
    }
}

object EmbyCompatibleProviderKinds {
    val Emby = ProviderKind("emby")
    val Jellyfin = ProviderKind("jellyfin")
}

@Serializable
@JvmInline
value class ProviderMediaKind(val value: String) {
    init {
        require(
            value.length <= MAX_LENGTH &&
                value.matches(PROVIDER_MEDIA_KIND_PATTERN)
        ) {
            "Provider media kind must be a lowercase identifier"
        }
    }

    override fun toString(): String = value

    companion object {
        const val MAX_LENGTH = 64
    }
}

object ProviderMediaKinds {
    val Unknown = ProviderMediaKind("unknown")
    val Live = ProviderMediaKind("live")
    val Movie = ProviderMediaKind("movie")
    val Series = ProviderMediaKind("series")
    val Season = ProviderMediaKind("season")
    val Episode = ProviderMediaKind("episode")
}

object SubscriptionProviderSettingKeys {
    const val BaseUrl = "base_url"
    const val Username = "username"
    const val Password = "password"
}

object ProviderAuthenticationContextKeys {
    const val ServerId = "server_id"
    const val UserId = "user_id"
}

object SubscriptionProviderErrorCodes {
    val AuthenticationFailed = ExtensionErrorCode("provider.authentication_failed")
}

@Serializable
data class SubscriptionProviderDiscoverRequest(
    val localeTag: String? = null,
) : ExtensionPayload

@Serializable
data class SubscriptionProviderVariant(
    val kind: ProviderKind,
    val displayName: String,
    val userSelectable: Boolean = true,
) {
    init {
        require(displayName.isNotBlank()) { "Provider variant display name must not be blank" }
    }
}

@Serializable
data class SubscriptionProviderDescriptor(
    val providerId: ExtensionId,
    val displayName: String,
    val variants: List<SubscriptionProviderVariant>,
    val settingsSchema: ExtensionSettingSchema? = null,
) {
    init {
        require(displayName.isNotBlank()) { "Provider display name must not be blank" }
        require(variants.isNotEmpty()) { "Provider must declare at least one variant" }
        require(variants.map(SubscriptionProviderVariant::kind).distinct().size == variants.size) {
            "Provider variant kinds must be unique"
        }
    }
}

@Serializable
data class SubscriptionProviderDiscoverResult(
    val provider: SubscriptionProviderDescriptor,
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
sealed interface ProviderValidationEvidence {
    @Serializable
    @SerialName("trusted_direct")
    data class TrustedDirect(
        val account: ValidatedProviderAccount,
        val credential: CredentialHandle,
    ) : ProviderValidationEvidence

    @Serializable
    @SerialName("host_broker_receipt")
    data class HostBrokerReceipt(
        val receipt: ProviderAuthenticationReceipt,
    ) : ProviderValidationEvidence
}

@Serializable
data class SubscriptionProviderValidateResult(
    val evidence: ProviderValidationEvidence,
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
) : ExtensionPayload

@Serializable
data class PlaybackReference(
    val providerId: ExtensionId,
    val itemId: String,
    val mediaSourceId: String? = null,
    val sourceType: String,
) {
    init {
        require(itemId.isNotBlank() && itemId.encodeToByteArray().size <= MAX_ID_UTF8_BYTES) {
            "Playback item id is blank or too large"
        }
        require(
            mediaSourceId == null ||
                mediaSourceId.isNotBlank() &&
                mediaSourceId.encodeToByteArray().size <= MAX_ID_UTF8_BYTES
        ) {
            "Playback media source id is blank or too large"
        }
        require(
            sourceType.isNotBlank() &&
                sourceType.encodeToByteArray().size <= MAX_SOURCE_TYPE_UTF8_BYTES
        ) {
            "Playback source type is blank or too large"
        }
    }

    companion object {
        const val MAX_ID_UTF8_BYTES = 512
        const val MAX_SOURCE_TYPE_UTF8_BYTES = 128
    }
}

@Serializable
data class SubscriptionChannelDescriptor(
    val remoteId: String,
    val title: String,
    val logoUrl: String? = null,
    val category: String,
    val playbackReference: PlaybackReference,
)

@Serializable
data class SubscriptionSourceDescriptor(
    val remoteId: String,
    val providerKind: ProviderKind,
)

@Serializable
data class SubscriptionContentRefreshResult(
    val source: SubscriptionSourceDescriptor,
    val channels: List<SubscriptionChannelDescriptor>,
) : ExtensionPayload

@Serializable
data class SubscriptionContentBrowseRequest(
    val account: ProviderAccountReference,
    val credential: ProviderCredential,
    val parentReference: PlaybackReference? = null,
    val cursor: String? = null,
    val limit: Int = DEFAULT_LIMIT,
) : ExtensionPayload {
    init {
        require(
            cursor == null ||
                cursor.isNotBlank() &&
                cursor.encodeToByteArray().size <= MAX_CURSOR_UTF8_BYTES
        ) {
            "Browse cursor is blank or too large"
        }
        require(limit in 1..MAX_LIMIT) {
            "Browse limit must be between 1 and $MAX_LIMIT"
        }
    }

    companion object {
        const val DEFAULT_LIMIT = 100
        const val MAX_LIMIT = 200
        const val MAX_CURSOR_UTF8_BYTES = 512
    }
}

@Serializable
data class SubscriptionContentItemDescriptor(
    val reference: PlaybackReference,
    val mediaKind: ProviderMediaKind,
    val title: String,
    val playable: Boolean,
    val browsable: Boolean,
    val imageUrl: String? = null,
    val category: String? = null,
    val subtitle: String? = null,
    val overview: String? = null,
    val productionYear: Int? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
) {
    init {
        require(playable || browsable) {
            "Provider media item must be playable, browsable, or both"
        }
        require(
            title.isNotBlank() &&
                title.encodeToByteArray().size <= MAX_TITLE_UTF8_BYTES
        ) {
            "Provider media title is blank or too large"
        }
        require(
            imageUrl == null ||
                imageUrl.isNotBlank() &&
                imageUrl.encodeToByteArray().size <= MAX_IMAGE_URL_UTF8_BYTES
        ) {
            "Provider media image URL is blank or too large"
        }
        require(
            category == null ||
                category.isNotBlank() &&
                category.encodeToByteArray().size <= MAX_CATEGORY_UTF8_BYTES
        ) {
            "Provider media category is blank or too large"
        }
        require(
            subtitle == null ||
                subtitle.isNotBlank() &&
                subtitle.encodeToByteArray().size <= MAX_SUBTITLE_UTF8_BYTES
        ) {
            "Provider media subtitle is blank or too large"
        }
        require(
            overview == null ||
                overview.isNotBlank() &&
                overview.encodeToByteArray().size <= MAX_OVERVIEW_UTF8_BYTES
        ) {
            "Provider media overview is blank or too large"
        }
        require(productionYear == null || productionYear in 1..MAX_PRODUCTION_YEAR) {
            "Provider media production year is out of range"
        }
        require(seasonNumber == null || seasonNumber in 0..MAX_INDEX_NUMBER) {
            "Provider media season number is out of range"
        }
        require(episodeNumber == null || episodeNumber in 0..MAX_INDEX_NUMBER) {
            "Provider media episode number is out of range"
        }
    }

    companion object {
        const val MAX_TITLE_UTF8_BYTES = 1_024
        const val MAX_IMAGE_URL_UTF8_BYTES = 8_192
        const val MAX_CATEGORY_UTF8_BYTES = 1_024
        const val MAX_SUBTITLE_UTF8_BYTES = 1_024
        const val MAX_OVERVIEW_UTF8_BYTES = 8_192
        const val MAX_PRODUCTION_YEAR = 9_999
        const val MAX_INDEX_NUMBER = 1_000_000
    }
}

@Serializable
data class SubscriptionContentBrowseResult(
    val items: List<SubscriptionContentItemDescriptor>,
    val nextCursor: String? = null,
    val total: Int? = null,
) : ExtensionPayload {
    init {
        require(items.size <= MAX_ITEMS_PER_PAGE) {
            "Provider media page contains too many items"
        }
        require(
            nextCursor == null ||
                nextCursor.isNotBlank() &&
                nextCursor.encodeToByteArray().size <= MAX_CURSOR_UTF8_BYTES
        ) {
            "Next browse cursor is blank or too large"
        }
        require(total == null || total >= 0) {
            "Provider media total must not be negative"
        }
    }

    companion object {
        const val MAX_ITEMS_PER_PAGE = SubscriptionContentBrowseRequest.MAX_LIMIT
        const val MAX_CURSOR_UTF8_BYTES =
            SubscriptionContentBrowseRequest.MAX_CURSOR_UTF8_BYTES
    }
}

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
) {
    init {
        require(playSessionId != null || liveStreamId != null) {
            "Playback session must contain at least one identifier"
        }
        listOfNotNull(playSessionId, liveStreamId).forEach { identifier ->
            require(identifier.isNotBlank()) {
                "Playback session identifiers must not be blank"
            }
            require(identifier.encodeToByteArray().size <= MAX_IDENTIFIER_UTF8_BYTES) {
                "Playback session identifier is too large"
            }
        }
    }

    companion object {
        const val MAX_IDENTIFIER_UTF8_BYTES = 512
    }
}

@Serializable
@JvmInline
value class PlaybackMethod(val value: String) {
    init {
        require(
            value.length <= MAX_LENGTH &&
                value.matches(PLAYBACK_METHOD_PATTERN)
        ) {
            "Playback method must be a lowercase identifier"
        }
    }

    override fun toString(): String = value

    companion object {
        const val MAX_LENGTH = 64
    }
}

object PlaybackMethods {
    val Unknown = PlaybackMethod("unknown")
    val DirectPlay = PlaybackMethod("direct_play")
    val DirectStream = PlaybackMethod("direct_stream")
    val Transcode = PlaybackMethod("transcode")
}

@Serializable
data class PlaybackHeaderValue(
    val parts: List<BrokerValue>,
) {
    init {
        require(parts.isNotEmpty()) { "Playback header value must not be empty" }
        require(parts.size <= 8) { "Playback header value has too many parts" }
    }

    companion object {
        fun literal(value: String): PlaybackHeaderValue =
            PlaybackHeaderValue(listOf(BrokerValue.Literal(value)))
    }
}

@Serializable
data class PlaybackSourceResolveResult(
    val url: String,
    val headers: Map<String, PlaybackHeaderValue> = emptyMap(),
    val mediaSourceId: String? = null,
    val session: PlaybackSessionDescriptor? = null,
    val playMethod: PlaybackMethod,
) : ExtensionPayload {
    init {
        require(url.isNotBlank() && url.encodeToByteArray().size <= MAX_URL_UTF8_BYTES) {
            "Playback URL is blank or too large"
        }
        require(
            mediaSourceId == null ||
                mediaSourceId.isNotBlank() &&
                mediaSourceId.encodeToByteArray().size <= PlaybackReference.MAX_ID_UTF8_BYTES
        ) {
            "Resolved media source id is blank or too large"
        }
    }

    companion object {
        const val MAX_URL_UTF8_BYTES = 8_192
    }
}

@Serializable
@JvmInline
value class PlaybackSessionEvent(val value: String) {
    init {
        require(
            value.length <= MAX_LENGTH &&
                value.matches(PLAYBACK_SESSION_EVENT_PATTERN)
        ) {
            "Playback session event must be a lowercase identifier"
        }
    }

    override fun toString(): String = value

    companion object {
        const val MAX_LENGTH = 64
    }
}

object PlaybackSessionEvents {
    val Started = PlaybackSessionEvent("started")
    val Progress = PlaybackSessionEvent("progress")
    val Paused = PlaybackSessionEvent("paused")
    val Resumed = PlaybackSessionEvent("resumed")
}

@Serializable
data class PlaybackSessionUpdateRequest(
    val account: ProviderAccountReference,
    val credential: ProviderCredential,
    val reference: PlaybackReference,
    val session: PlaybackSessionDescriptor,
    val event: PlaybackSessionEvent,
    val positionTicks: Long,
    val playMethod: PlaybackMethod,
    val isPaused: Boolean,
) : ExtensionPayload {
    init {
        require(positionTicks >= 0L) {
            "Playback position must not be negative"
        }
    }
}

@Serializable
data class PlaybackSessionUpdateResult(
    val accepted: Boolean,
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
    val positionTicks: Long,
) : ExtensionPayload {
    init {
        require(positionTicks >= 0L) {
            "Playback position must not be negative"
        }
    }
}

@Serializable
data class PlaybackSessionCloseResult(
    val closed: Boolean,
) : ExtensionPayload

object SubscriptionHookSpecs {
    val Discover = HookSpec(
        hook = ExtensionHookIds.SubscriptionProviderDiscover,
        schemaVersion = 4,
        requestSerializer = SubscriptionProviderDiscoverRequest.serializer(),
        responseSerializer = SubscriptionProviderDiscoverResult.serializer(),
    )
    val Validate = HookSpec(
        hook = ExtensionHookIds.SubscriptionProviderValidate,
        schemaVersion = 2,
        requestSerializer = SubscriptionProviderValidateRequest.serializer(),
        responseSerializer = SubscriptionProviderValidateResult.serializer(),
    )
    val Refresh = HookSpec(
        hook = ExtensionHookIds.SubscriptionContentRefresh,
        schemaVersion = 4,
        requestSerializer = SubscriptionContentRefreshRequest.serializer(),
        responseSerializer = SubscriptionContentRefreshResult.serializer(),
    )
    val Browse = HookSpec(
        hook = ExtensionHookIds.SubscriptionContentBrowse,
        schemaVersion = 1,
        requestSerializer = SubscriptionContentBrowseRequest.serializer(),
        responseSerializer = SubscriptionContentBrowseResult.serializer(),
    )
    val ResolvePlayback = HookSpec(
        hook = ExtensionHookIds.PlaybackSourceResolve,
        schemaVersion = 4,
        requestSerializer = PlaybackSourceResolveRequest.serializer(),
        responseSerializer = PlaybackSourceResolveResult.serializer(),
    )
    val UpdatePlayback = HookSpec(
        hook = ExtensionHookIds.PlaybackSessionUpdate,
        schemaVersion = 1,
        requestSerializer = PlaybackSessionUpdateRequest.serializer(),
        responseSerializer = PlaybackSessionUpdateResult.serializer(),
    )
    val ClosePlayback = HookSpec(
        hook = ExtensionHookIds.PlaybackSessionClose,
        schemaVersion = 3,
        requestSerializer = PlaybackSessionCloseRequest.serializer(),
        responseSerializer = PlaybackSessionCloseResult.serializer(),
    )
}
