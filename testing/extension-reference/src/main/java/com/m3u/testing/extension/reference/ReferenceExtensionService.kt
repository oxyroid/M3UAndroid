package com.m3u.testing.extension.reference

import com.m3u.extension.api.BackgroundTaskRequest
import com.m3u.extension.api.BackgroundTaskResult
import com.m3u.extension.api.ChannelMetadataPatch
import com.m3u.extension.api.EpgRefreshResult
import com.m3u.extension.api.ExtensionApiRange
import com.m3u.extension.api.ExtensionApiVersions
import com.m3u.extension.api.ExtensionBackgroundTaskDeclaration
import com.m3u.extension.api.ExtensionCapabilityIds
import com.m3u.extension.api.ExtensionCapabilityRequest
import com.m3u.extension.api.ExtensionError
import com.m3u.extension.api.ExtensionErrorCode
import com.m3u.extension.api.ExtensionHookDeclaration
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.ExtensionManifest
import com.m3u.extension.api.ExtensionNetworkOrigin
import com.m3u.extension.api.ExtensionProgramme
import com.m3u.extension.api.ExtensionPayload
import com.m3u.extension.api.ExtensionSemanticVersion
import com.m3u.extension.api.ExtensionSettingChoice
import com.m3u.extension.api.ExtensionSettingField
import com.m3u.extension.api.ExtensionSettingSchema
import com.m3u.extension.api.ExtensionSettingSection
import com.m3u.extension.api.ExtensionSettingsSnapshot
import com.m3u.extension.api.ExtensionSettingType
import com.m3u.extension.api.HostHookSpecs
import com.m3u.extension.api.HookResult
import com.m3u.extension.api.InvocationId
import com.m3u.extension.api.MetadataEnrichmentResult
import com.m3u.extension.api.SearchProviderItem
import com.m3u.extension.api.SearchProviderRequest
import com.m3u.extension.api.SearchProviderResult
import com.m3u.extension.api.SettingsSchemaResult
import com.m3u.extension.api.security.BrokerAuthenticationRequest
import com.m3u.extension.api.security.BrokerAuthenticationResponse
import com.m3u.extension.api.security.BrokerHttpExchange
import com.m3u.extension.api.security.BrokeredHttpRequest
import com.m3u.extension.api.security.BrokeredHttpResponse
import com.m3u.extension.api.security.BrokerValue
import com.m3u.extension.api.security.BrokerValueEncoding
import com.m3u.extension.api.security.ContextReference
import com.m3u.extension.api.security.CredentialHandle
import com.m3u.extension.api.security.OpaqueContextCapture
import com.m3u.extension.api.security.ResponseValueSource
import com.m3u.extension.api.security.SecretReference
import com.m3u.extension.api.subscription.PlaybackHeaderValue
import com.m3u.extension.api.subscription.PlaybackReference
import com.m3u.extension.api.subscription.PlaybackSessionCloseRequest
import com.m3u.extension.api.subscription.PlaybackSessionCloseResult
import com.m3u.extension.api.subscription.PlaybackSessionDescriptor
import com.m3u.extension.api.subscription.PlaybackSourceResolveRequest
import com.m3u.extension.api.subscription.PlaybackSourceResolveResult
import com.m3u.extension.api.subscription.ProviderAccountReference
import com.m3u.extension.api.subscription.ProviderAuthenticationContextKeys
import com.m3u.extension.api.subscription.ProviderKind
import com.m3u.extension.api.subscription.ProviderValidationEvidence
import com.m3u.extension.api.subscription.SubscriptionChannelDescriptor
import com.m3u.extension.api.subscription.SubscriptionContentRefreshRequest
import com.m3u.extension.api.subscription.SubscriptionContentRefreshResult
import com.m3u.extension.api.subscription.SubscriptionHookSpecs
import com.m3u.extension.api.subscription.SubscriptionProviderDescriptor
import com.m3u.extension.api.subscription.SubscriptionProviderDiscoverResult
import com.m3u.extension.api.subscription.SubscriptionProviderErrorCodes
import com.m3u.extension.api.subscription.SubscriptionProviderSettingKeys
import com.m3u.extension.api.subscription.SubscriptionProviderVariant
import com.m3u.extension.api.subscription.SubscriptionProviderValidateRequest
import com.m3u.extension.api.subscription.SubscriptionProviderValidateResult
import com.m3u.extension.api.subscription.SubscriptionSourceDescriptor
import com.m3u.extension.sdk.android.BrokerException
import com.m3u.extension.sdk.android.ExtensionHostNetworkBroker
import com.m3u.extension.sdk.android.TypedExtensionService
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

class ReferenceExtensionService : TypedExtensionService() {
    override val extensionManifest: ExtensionManifest = REFERENCE_MANIFEST
    private val invocationGateProbeCount = AtomicInteger()

    init {
        handle(SubscriptionHookSpecs.Discover) { _, _ ->
            discoverProvider()
        }
        handleResultWithBroker(SubscriptionHookSpecs.Validate) { request, _, broker ->
            providerBrokerResult("validation") { validateProvider(request, broker) }
        }
        handleResultWithBroker(SubscriptionHookSpecs.Refresh) { request, _, broker ->
            providerBrokerResult("refresh") { refreshProvider(request, broker) }
        }
        handleResultWithBroker(SubscriptionHookSpecs.ResolvePlayback) { request, _, broker ->
            providerBrokerResult("playback resolution") {
                resolveProviderPlayback(request, broker)
            }
        }
        handleResultWithBroker(SubscriptionHookSpecs.ClosePlayback) { request, _, broker ->
            providerBrokerResult("playback close") {
                closeProviderPlayback(request, broker)
            }
        }
        handleResultWithBroker(HostHookSpecs.SearchProvider) { request, _, broker ->
            providerBrokerResult("search") { searchProvider(request, broker) }
        }
        handle(HostHookSpecs.BackgroundTask) { request, context ->
            runBackgroundTask(context.invocationId, request, context.settings)
        }
        handle(HostHookSpecs.MetadataEnrichment) { request, _ ->
            MetadataEnrichmentResult(
                patches = request.channels.map { channel ->
                    ChannelMetadataPatch(
                        stableReference = channel.stableReference,
                        title = when (channel.stableReference) {
                            INVOCATION_GATE_RESET_REFERENCE -> {
                                invocationGateProbeCount.set(0)
                                INVOCATION_GATE_COUNT_TITLE_PREFIX + "0"
                            }
                            INVOCATION_GATE_COUNT_REFERENCE ->
                                INVOCATION_GATE_COUNT_TITLE_PREFIX +
                                    invocationGateProbeCount.incrementAndGet()
                            else -> channel.title.takeIf { it.startsWith("unenriched:") }
                                ?.removePrefix("unenriched:")
                        },
                    )
                }
            )
        }
        handle(HostHookSpecs.EpgRefresh) { request, _ ->
            EpgRefreshResult(
                programmes = request.sourceIds.map { reference ->
                    ExtensionProgramme(
                        channelReference = reference,
                        title = "Reference programme",
                        startEpochMillis = request.fromEpochMillis,
                        endEpochMillis = request.toEpochMillis,
                        categories = listOf("Reference", "Conformance"),
                    )
                }
            )
        }
        handle(HostHookSpecs.SettingsSchema) { _, _ ->
            SettingsSchemaResult(
                sections = listOf(
                    ExtensionSettingSection(
                        id = "playback",
                        title = "Playback",
                        schema = ExtensionSettingSchema(
                            version = 1,
                            fields = listOf(
                                ExtensionSettingField(
                                    key = "quality",
                                    label = "Quality",
                                    type = ExtensionSettingType.SINGLE_CHOICE,
                                    choices = listOf(
                                        ExtensionSettingChoice("auto", "Automatic"),
                                        ExtensionSettingChoice("direct", "Direct play"),
                                    ),
                                    defaultValue = JsonPrimitive("auto"),
                                )
                            ),
                        ),
                    )
                )
            )
        }
    }
}

private suspend fun searchProvider(
    request: SearchProviderRequest,
    broker: ExtensionHostNetworkBroker,
): HookResult<SearchProviderResult> {
    val account = request.account
    if (account == null) {
        val response = broker.execute(
            BrokeredHttpRequest(method = "GET", url = REFERENCE_SEARCH_PROBE_ORIGIN + "/probe")
        )
        response.providerFailure("search")?.let { return it }
        return HookResult.Success(
            SearchProviderResult(
                items = if (request.query == "large") {
                    List(LARGE_RESULT_ITEM_COUNT) { index ->
                        SearchProviderItem(
                            accountId = "fixture-account",
                            remoteId = "large-$index-${"x".repeat(48)}",
                        )
                    }
                } else {
                    listOf(
                        SearchProviderItem(
                            accountId = "fixture-account",
                            remoteId = request.query,
                        )
                    )
                }
            )
        )
    }
    val response = broker.execute(request.referenceSearchRequest())
    return response.providerResponseResult("search") {
        val payload = response.body.decodeReferencePayload<ReferenceChannelsPayload>()
        val query = request.query.trim()
        SearchProviderResult(
            items = payload.channels
                .filter { channel ->
                    channel.id.contains(query, ignoreCase = true) ||
                        channel.title.contains(query, ignoreCase = true)
                }
                .map { channel ->
                    SearchProviderItem(
                        accountId = account.accountId,
                        remoteId = channel.id,
                    )
                }
        )
    }
}

internal fun SearchProviderRequest.referenceSearchRequest(): BrokeredHttpRequest {
    val selectedAccount = requireNotNull(account) {
        "Reference provider search requires a selected account"
    }
    selectedAccount.requireReferenceAccount()
    val selectedCredential = requireNotNull(credential) {
        "Reference provider search requires the selected account credential"
    }
    return BrokeredHttpRequest(
        method = "GET",
        url = selectedAccount.baseUrl.referenceEndpoint("channels"),
        headers = selectedCredential.handle.referenceRequestHeaders(),
        maximumResponseBytes = MAX_REFRESH_RESPONSE_BYTES,
    )
}

private fun discoverProvider(): SubscriptionProviderDiscoverResult =
    SubscriptionProviderDiscoverResult(
        provider = SubscriptionProviderDescriptor(
            providerId = REFERENCE_EXTENSION_ID,
            displayName = "Reference Provider",
            variants = listOf(
                SubscriptionProviderVariant(
                    kind = REFERENCE_PROVIDER_KIND,
                    displayName = "Reference",
                )
            ),
            settingsSchema = REFERENCE_PROVIDER_SETTINGS,
        )
    )

private suspend fun validateProvider(
    request: SubscriptionProviderValidateRequest,
    broker: ExtensionHostNetworkBroker,
): HookResult<SubscriptionProviderValidateResult> {
    val call = request.referenceLoginCall()
    val response = broker.authenticate(call.request)
    response.providerFailure("login")?.let { return it }
    return HookResult.Success(
        SubscriptionProviderValidateResult(
            evidence = ProviderValidationEvidence.HostBrokerReceipt(
                receipt = requireNotNull(response.receipt) {
                    "Reference provider login did not return an authentication receipt"
                },
            ),
        ),
    )
}

private suspend fun refreshProvider(
    request: SubscriptionContentRefreshRequest,
    broker: ExtensionHostNetworkBroker,
): HookResult<SubscriptionContentRefreshResult> =
    broker.execute(request.referenceRefreshRequest()).referenceRefreshResult(request)

internal fun SubscriptionContentRefreshRequest.referenceRefreshRequest(): BrokeredHttpRequest {
    account.requireReferenceAccount()
    return BrokeredHttpRequest(
        method = "GET",
        url = account.baseUrl.referenceEndpoint("channels"),
        headers = credential.handle.referenceRequestHeaders(),
        maximumResponseBytes = MAX_REFRESH_RESPONSE_BYTES,
    )
}

internal fun BrokeredHttpResponse.referenceRefreshResult(
    request: SubscriptionContentRefreshRequest,
): HookResult<SubscriptionContentRefreshResult> =
    providerResponseResult("refresh") {
        val payload = body.decodeReferencePayload<ReferenceChannelsPayload>()
        requireReferencePayload(payload.sourceId.matches(REFERENCE_ID_PATTERN))
        requireReferencePayload(payload.sourceTitle.isValidProviderText())
        requireReferencePayload(payload.revision.matches(REFERENCE_REVISION_PATTERN))
        requireReferencePayload(payload.channels.isNotEmpty())
        requireReferencePayload(payload.channels.size <= MAX_REFERENCE_CHANNELS)
        requireReferencePayload(
            payload.channels.map(ReferenceChannelPayload::id).distinct().size ==
                payload.channels.size
        )
        SubscriptionContentRefreshResult(
            source = SubscriptionSourceDescriptor(
                remoteId = request.account.serverId,
                providerKind = REFERENCE_PROVIDER_KIND,
            ),
            channels = payload.channels.map { channel ->
                requireReferencePayload(channel.id.matches(REFERENCE_ID_PATTERN))
                requireReferencePayload(channel.title.isValidProviderText())
                requireReferencePayload(channel.category.isValidProviderText())
                requireReferencePayload(channel.epgReference == channel.id)
                SubscriptionChannelDescriptor(
                    remoteId = channel.id,
                    title = channel.title,
                    category = channel.category,
                    playbackReference = PlaybackReference(
                        providerId = REFERENCE_EXTENSION_ID,
                        itemId = channel.id,
                        sourceType = REFERENCE_SOURCE_TYPE,
                    ),
                )
            },
        )
    }

private suspend fun resolveProviderPlayback(
    request: PlaybackSourceResolveRequest,
    broker: ExtensionHostNetworkBroker,
): HookResult<PlaybackSourceResolveResult> =
    broker.execute(request.referencePlaybackRequest()).referencePlaybackResult(request)

internal fun PlaybackSourceResolveRequest.referencePlaybackRequest(): BrokeredHttpRequest {
    account.requireReferenceAccount()
    reference.requireReferencePlayback()
    return BrokeredHttpRequest(
        method = "GET",
        url = account.baseUrl.referenceEndpoint("playback/${reference.itemId}"),
        headers = credential.handle.referenceRequestHeaders(),
        maximumResponseBytes = MAX_PLAYBACK_RESPONSE_BYTES,
    )
}

internal fun BrokeredHttpResponse.referencePlaybackResult(
    request: PlaybackSourceResolveRequest,
): HookResult<PlaybackSourceResolveResult> =
    providerResponseResult("playback resolution") {
        val payload = body.decodeReferencePayload<ReferencePlaybackPayload>()
        val expectedStreamUrl = request.account.baseUrl.referenceEndpoint(
            "stream/${request.reference.itemId}/index.m3u8"
        )
        requireReferencePayload(payload.url == expectedStreamUrl)
        requireReferencePayload(
            payload.mediaSourceId == "reference-media-${request.reference.itemId}"
        )
        requireReferencePayload(
            payload.playSessionId == "reference-play-session-${request.reference.itemId}"
        )
        requireReferencePayload(
            payload.liveStreamId == "reference-live-stream-${request.reference.itemId}"
        )
        requireReferencePayload(payload.playSessionId.isValidSessionIdentifier())
        requireReferencePayload(payload.liveStreamId.isValidSessionIdentifier())
        PlaybackSourceResolveResult(
            url = payload.url,
            headers = request.credential.handle.referencePlaybackHeaders(),
            mediaSourceId = payload.mediaSourceId,
            session = PlaybackSessionDescriptor(
                playSessionId = payload.playSessionId,
                liveStreamId = payload.liveStreamId,
            ),
        )
    }

private suspend fun closeProviderPlayback(
    request: PlaybackSessionCloseRequest,
    broker: ExtensionHostNetworkBroker,
): HookResult<PlaybackSessionCloseResult> =
    broker.execute(request.referenceCloseRequest()).referenceCloseResult()

internal fun PlaybackSessionCloseRequest.referenceCloseRequest(): BrokeredHttpRequest {
    account.requireReferenceAccount()
    reference.requireReferencePlayback()
    val playSessionId = requireNotNull(session.playSessionId) {
        "Reference playback close requires a play session ID"
    }
    val liveStreamId = requireNotNull(session.liveStreamId) {
        "Reference playback close requires a live stream ID"
    }
    require(playSessionId.isValidSessionIdentifier())
    require(liveStreamId.isValidSessionIdentifier())
    return BrokeredHttpRequest(
        method = "POST",
        url = account.baseUrl.referenceEndpoint("sessions/close"),
        headers = credential.handle.referenceRequestHeaders() +
            ("Content-Type" to BrokerValue.Literal("application/json")),
        body = listOf(
            BrokerValue.Literal(
                referenceJson.encodeToString(
                    ReferenceClosePayload(
                        itemId = reference.itemId,
                        playSessionId = playSessionId,
                        liveStreamId = liveStreamId,
                        reason = reason.value,
                    )
                )
            )
        ),
        maximumResponseBytes = MAX_CLOSE_RESPONSE_BYTES,
    )
}

internal fun BrokeredHttpResponse.referenceCloseResult(): HookResult<PlaybackSessionCloseResult> =
    providerResponseResult("playback close") {
        val payload = body.decodeReferencePayload<ReferenceCloseResultPayload>()
        PlaybackSessionCloseResult(closed = payload.closed)
    }

private suspend fun runBackgroundTask(
    invocationId: InvocationId,
    request: BackgroundTaskRequest,
    settings: ExtensionSettingsSnapshot,
): BackgroundTaskResult {
    if (request.taskId == "settings-status") {
        return BackgroundTaskResult(
            mapOf(
                "enabled" to settings.values["manifest/enabled"].toString(),
                "hasApiKey" to settings.credentialHandles
                    .containsKey("manifest/api-key")
                    .toString(),
            )
        )
    }
    if (request.taskId == "cancel-status") {
        return BackgroundTaskResult(
            mapOf("cancelled" to (lastCancelled.get() != null).toString())
        )
    }
    return try {
        delay(10_000)
        BackgroundTaskResult(mapOf("completed" to "true"))
    } finally {
        if (!currentCoroutineContext().isActive) {
            lastCancelled.set(invocationId.value)
        }
    }
}

internal data class ReferenceLoginCall(
    val baseUrl: String,
    val request: BrokerAuthenticationRequest,
)

internal fun SubscriptionProviderValidateRequest.referenceLoginCall(): ReferenceLoginCall {
    require(providerKind == REFERENCE_PROVIDER_KIND) {
        "Unsupported reference provider kind"
    }
    val baseUrl = settingValues.required(SubscriptionProviderSettingKeys.BaseUrl)
        .normalizeBaseUrl()
    val username = settingValues.required(SubscriptionProviderSettingKeys.Username)
    val password = credentialHandles[SubscriptionProviderSettingKeys.Password]
        ?: error("Reference provider password is required")
    return ReferenceLoginCall(
        baseUrl = baseUrl,
        request = BrokerAuthenticationRequest(
            exchange = BrokerHttpExchange(
                method = "POST",
                url = baseUrl.referenceEndpoint("login"),
                headers = mapOf(
                    "Content-Type" to BrokerValue.Literal("application/json"),
                ),
                body = listOf(
                    BrokerValue.Literal("{\"username\":"),
                    BrokerValue.Encoded(
                        value = BrokerValue.Literal(username),
                        encoding = BrokerValueEncoding.JsonString,
                    ),
                    BrokerValue.Literal(",\"password\":"),
                    BrokerValue.Encoded(
                        value = BrokerValue.Secret(SecretReference(password)),
                        encoding = BrokerValueEncoding.JsonString,
                    ),
                    BrokerValue.Literal("}"),
                ),
            ),
            primaryCredentialSource = ResponseValueSource.JsonPointer("/accessToken"),
            opaqueContexts = listOf(
                OpaqueContextCapture(
                    key = ProviderAuthenticationContextKeys.ServerId,
                    source = ResponseValueSource.JsonPointer("/server_id"),
                ),
                OpaqueContextCapture(
                    key = ProviderAuthenticationContextKeys.UserId,
                    source = ResponseValueSource.JsonPointer("/user_id"),
                ),
            ),
        ),
    )
}

private fun ProviderAccountReference.requireReferenceAccount() {
    require(providerId == REFERENCE_EXTENSION_ID) {
        "Provider account belongs to another extension"
    }
    require(providerKind == REFERENCE_PROVIDER_KIND) {
        "Provider account has an unsupported kind"
    }
}

private fun PlaybackReference.requireReferencePlayback() {
    require(providerId == REFERENCE_EXTENSION_ID) {
        "Playback reference belongs to another provider"
    }
    require(sourceType == REFERENCE_SOURCE_TYPE) {
        "Reference provider supports only live playback"
    }
    require(itemId.matches(REFERENCE_ID_PATTERN)) {
        "Reference playback item ID is invalid"
    }
}

private fun CredentialHandle.referenceRequestHeaders(): Map<String, BrokerValue> = mapOf(
    REFERENCE_TOKEN_HEADER to BrokerValue.Secret(SecretReference(this)),
    REFERENCE_USER_HEADER to BrokerValue.Context(
        ContextReference(ProviderAuthenticationContextKeys.UserId)
    ),
)

private fun CredentialHandle.referencePlaybackHeaders(): Map<String, PlaybackHeaderValue> = mapOf(
    REFERENCE_TOKEN_HEADER to PlaybackHeaderValue(
        parts = listOf(BrokerValue.Secret(SecretReference(this))),
    ),
    REFERENCE_USER_HEADER to PlaybackHeaderValue(
        parts = listOf(
            BrokerValue.Context(ContextReference(ProviderAuthenticationContextKeys.UserId))
        ),
    ),
)

private fun BrokerAuthenticationResponse.providerFailure(operation: String): HookResult.Failure? =
    statusCode.providerFailure(operation)

private fun BrokeredHttpResponse.providerFailure(operation: String): HookResult.Failure? =
    statusCode.providerFailure(operation)

private inline fun <Response : ExtensionPayload> BrokeredHttpResponse.providerResponseResult(
    operation: String,
    decode: () -> Response,
): HookResult<Response> {
    providerFailure(operation)?.let { return it }
    return try {
        HookResult.Success(decode())
    } catch (_: ReferenceProviderPayloadException) {
        invalidProviderResponse(operation)
    }
}

private fun Int.providerFailure(operation: String): HookResult.Failure? {
    if (this in 200..299) return null
    val (code, message) = when (this) {
        401, 403 ->
            SubscriptionProviderErrorCodes.AuthenticationFailed.value to
                "Provider credentials were rejected"
        408, 429 -> "provider.temporarily_unavailable" to "Provider is temporarily unavailable"
        in 500..599 -> "provider.remote_unavailable" to "Provider service is unavailable"
        else -> "provider.request_failed" to "Provider request was rejected"
    }
    return HookResult.Failure(
        ExtensionError(
            code = ExtensionErrorCode(code),
            message = message,
            recoverable = this == 401 || this == 403 || this == 408 ||
                this == 429 || this >= 500,
            details = mapOf(
                "operation" to operation,
                "status_code" to toString(),
            ),
        )
    )
}

private fun invalidProviderResponse(operation: String): HookResult.Failure =
    HookResult.Failure(
        ExtensionError(
            code = ExtensionErrorCode("provider.invalid_response"),
            message = "Provider returned an invalid $operation response",
            recoverable = false,
            details = mapOf("operation" to operation),
        )
    )

internal suspend fun <Response : ExtensionPayload> providerBrokerResult(
    operation: String,
    block: suspend () -> HookResult<Response>,
): HookResult<Response> = try {
    block()
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (failure: BrokerException) {
    HookResult.Failure(
        ExtensionError(
            code = ExtensionErrorCode("provider.broker_${failure.code.value}"),
            message = "Provider $operation could not be completed",
            recoverable = failure.recoverable,
            details = mapOf("broker_code" to failure.code.value),
        )
    )
}

private inline fun <reified Payload> String.decodeReferencePayload(): Payload = try {
    referenceJson.decodeFromString(this)
} catch (_: SerializationException) {
    throw ReferenceProviderPayloadException()
}

private fun requireReferencePayload(condition: Boolean) {
    if (!condition) throw ReferenceProviderPayloadException()
}

private fun String.isValidProviderText(): Boolean =
    isNotBlank() && length <= MAX_REFERENCE_TEXT_LENGTH && none(Char::isISOControl)

private fun String.isValidSessionIdentifier(): Boolean =
    isNotBlank() &&
        encodeToByteArray().size <= PlaybackSessionDescriptor.MAX_IDENTIFIER_UTF8_BYTES &&
        none(Char::isISOControl)

private class ReferenceProviderPayloadException : IllegalArgumentException()

private fun Map<String, String>.required(key: String): String =
    get(key)?.takeIf(String::isNotBlank)
        ?: error("Reference provider setting $key is required")

private fun String.normalizeBaseUrl(): String = trim().trimEnd('/').also { value ->
    require(value.startsWith("http://") || value.startsWith("https://")) {
        "Reference provider URL must use HTTP or HTTPS"
    }
}

private fun String.referenceEndpoint(path: String): String =
    "${normalizeBaseUrl()}/reference-provider/$path"

@Serializable
private data class ReferenceChannelsPayload(
    @SerialName("source_id")
    val sourceId: String,
    @SerialName("source_title")
    val sourceTitle: String,
    val revision: String,
    val channels: List<ReferenceChannelPayload>,
)

@Serializable
private data class ReferenceChannelPayload(
    val id: String,
    val title: String,
    val category: String,
    @SerialName("epg_reference")
    val epgReference: String,
)

@Serializable
private data class ReferencePlaybackPayload(
    val url: String,
    @SerialName("media_source_id")
    val mediaSourceId: String,
    @SerialName("play_session_id")
    val playSessionId: String,
    @SerialName("live_stream_id")
    val liveStreamId: String,
)

@Serializable
private data class ReferenceClosePayload(
    @SerialName("item_id")
    val itemId: String,
    @SerialName("play_session_id")
    val playSessionId: String,
    @SerialName("live_stream_id")
    val liveStreamId: String,
    val reason: String,
)

@Serializable
private data class ReferenceCloseResultPayload(
    val closed: Boolean,
)

internal val REFERENCE_EXTENSION_ID = ExtensionId("com.m3u.reference.provider")
internal val REFERENCE_PROVIDER_KIND = ProviderKind("reference")
const val LARGE_RESULT_ITEM_COUNT = 25_000
private val lastCancelled = AtomicReference<String?>(null)
private val referenceJson = Json {
    ignoreUnknownKeys = false
    isLenient = false
    coerceInputValues = false
    explicitNulls = true
}
private val REFERENCE_ID_PATTERN = Regex("[a-z0-9]+(?:[._-][a-z0-9]+)*")
private val REFERENCE_REVISION_PATTERN = Regex("[0-9]{1,18}")
private const val REFERENCE_SOURCE_TYPE = "live"
private const val REFERENCE_TOKEN_HEADER = "X-Emby-Token"
private const val REFERENCE_USER_HEADER = "X-Reference-User"
private const val MAX_REFERENCE_TEXT_LENGTH = 256
private const val MAX_REFERENCE_CHANNELS = 1_000
private const val MAX_REFRESH_RESPONSE_BYTES = 512 * 1_024
private const val MAX_PLAYBACK_RESPONSE_BYTES = 32 * 1_024
private const val MAX_CLOSE_RESPONSE_BYTES = 8 * 1_024
private const val REFERENCE_SEARCH_PROBE_ORIGIN = "https://reference.invalid"
private const val INVOCATION_GATE_RESET_REFERENCE = "test.invocation-gate.reset"
private const val INVOCATION_GATE_COUNT_REFERENCE = "test.invocation-gate.count"
private const val INVOCATION_GATE_COUNT_TITLE_PREFIX = "invocation-count:"

internal val REFERENCE_PROVIDER_SETTINGS = ExtensionSettingSchema(
    version = 1,
    fields = listOf(
        ExtensionSettingField(
            key = SubscriptionProviderSettingKeys.BaseUrl,
            label = "Server URL",
            type = ExtensionSettingType.TEXT,
            required = true,
        ),
        ExtensionSettingField(
            key = SubscriptionProviderSettingKeys.Username,
            label = "Username",
            type = ExtensionSettingType.TEXT,
            required = true,
            defaultValue = JsonPrimitive("m3u"),
        ),
        ExtensionSettingField(
            key = SubscriptionProviderSettingKeys.Password,
            label = "Password",
            type = ExtensionSettingType.SECRET,
            required = true,
        ),
    ),
)

internal val REFERENCE_MANIFEST = ExtensionManifest(
    id = REFERENCE_EXTENSION_ID,
    displayName = "Reference Provider",
    extensionVersion = ExtensionSemanticVersion(1, 0, 0),
    apiRange = ExtensionApiRange(ExtensionApiVersions.Current, ExtensionApiVersions.Current),
    hooks = setOf(
        ExtensionHookDeclaration(
            hook = SubscriptionHookSpecs.Discover.hook,
            schemaVersion = SubscriptionHookSpecs.Discover.schemaVersion,
        ),
        ExtensionHookDeclaration(
            hook = SubscriptionHookSpecs.Validate.hook,
            schemaVersion = SubscriptionHookSpecs.Validate.schemaVersion,
            requiredCapabilities = setOf(
                ExtensionCapabilityIds.Network,
                ExtensionCapabilityIds.CredentialRead,
                ExtensionCapabilityIds.CredentialWrite,
            ),
        ),
        ExtensionHookDeclaration(
            hook = SubscriptionHookSpecs.Refresh.hook,
            schemaVersion = SubscriptionHookSpecs.Refresh.schemaVersion,
            requiredCapabilities = setOf(
                ExtensionCapabilityIds.SubscriptionRead,
                ExtensionCapabilityIds.Network,
                ExtensionCapabilityIds.CredentialRead,
            ),
        ),
        ExtensionHookDeclaration(
            hook = SubscriptionHookSpecs.ResolvePlayback.hook,
            schemaVersion = SubscriptionHookSpecs.ResolvePlayback.schemaVersion,
            requiredCapabilities = setOf(
                ExtensionCapabilityIds.PlaybackResolve,
                ExtensionCapabilityIds.Network,
                ExtensionCapabilityIds.CredentialRead,
            ),
        ),
        ExtensionHookDeclaration(
            hook = SubscriptionHookSpecs.ClosePlayback.hook,
            schemaVersion = SubscriptionHookSpecs.ClosePlayback.schemaVersion,
            requiredCapabilities = setOf(
                ExtensionCapabilityIds.PlaybackResolve,
                ExtensionCapabilityIds.Network,
                ExtensionCapabilityIds.CredentialRead,
            ),
        ),
        ExtensionHookDeclaration(
            hook = HostHookSpecs.SearchProvider.hook,
            schemaVersion = HostHookSpecs.SearchProvider.schemaVersion,
            requiredCapabilities = setOf(
                ExtensionCapabilityIds.SearchRead,
                ExtensionCapabilityIds.Network,
                ExtensionCapabilityIds.CredentialRead,
            ),
        ),
        ExtensionHookDeclaration(
            hook = HostHookSpecs.BackgroundTask.hook,
            schemaVersion = HostHookSpecs.BackgroundTask.schemaVersion,
            requiredCapabilities = setOf(ExtensionCapabilityIds.BackgroundTask),
        ),
        ExtensionHookDeclaration(
            hook = HostHookSpecs.MetadataEnrichment.hook,
            schemaVersion = HostHookSpecs.MetadataEnrichment.schemaVersion,
            requiredCapabilities = setOf(ExtensionCapabilityIds.MetadataWrite),
        ),
        ExtensionHookDeclaration(
            hook = HostHookSpecs.EpgRefresh.hook,
            schemaVersion = HostHookSpecs.EpgRefresh.schemaVersion,
            requiredCapabilities = setOf(ExtensionCapabilityIds.EpgRead),
        ),
        ExtensionHookDeclaration(
            hook = HostHookSpecs.SettingsSchema.hook,
            schemaVersion = HostHookSpecs.SettingsSchema.schemaVersion,
            requiredCapabilities = setOf(ExtensionCapabilityIds.SettingsContribute),
        ),
    ),
    capabilities = setOf(
        ExtensionCapabilityRequest(
            ExtensionCapabilityIds.Network,
            "Connect to the configured reference provider",
        ),
        ExtensionCapabilityRequest(
            ExtensionCapabilityIds.CredentialRead,
            "Use provider credentials through host-managed requests",
        ),
        ExtensionCapabilityRequest(
            ExtensionCapabilityIds.CredentialWrite,
            "Capture the reference provider access token",
        ),
        ExtensionCapabilityRequest(
            ExtensionCapabilityIds.SubscriptionRead,
            "Refresh the reference provider subscription",
        ),
        ExtensionCapabilityRequest(
            ExtensionCapabilityIds.PlaybackResolve,
            "Resolve and close reference playback sessions",
        ),
        ExtensionCapabilityRequest(
            ExtensionCapabilityIds.SearchRead,
            "Return reference search results",
        ),
        ExtensionCapabilityRequest(
            ExtensionCapabilityIds.BackgroundTask,
            "Exercise cancellation conformance",
        ),
        ExtensionCapabilityRequest(
            ExtensionCapabilityIds.MetadataWrite,
            "Exercise host-owned metadata import",
        ),
        ExtensionCapabilityRequest(
            ExtensionCapabilityIds.EpgRead,
            "Exercise host-owned EPG import",
        ),
        ExtensionCapabilityRequest(
            ExtensionCapabilityIds.SettingsContribute,
            "Exercise declarative extension settings",
        ),
    ),
    settingsSchema = ExtensionSettingSchema(
        version = 1,
        fields = listOf(
            ExtensionSettingField(
                key = "enabled",
                label = "Enabled",
                type = ExtensionSettingType.BOOLEAN,
                defaultValue = JsonPrimitive(true),
            ),
            ExtensionSettingField(
                key = "api-key",
                label = "API key",
                type = ExtensionSettingType.SECRET,
            ),
        ),
    ),
    metadata = mapOf("developer" to "M3U Conformance Suite"),
    networkOrigins = setOf(ExtensionNetworkOrigin(REFERENCE_SEARCH_PROBE_ORIGIN)),
    backgroundTasks = listOf(
        ExtensionBackgroundTaskDeclaration(
            taskId = "settings-status",
            repeatIntervalHours = 24,
        )
    ),
)
