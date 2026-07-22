package com.m3u.testing.extension.reference

import com.m3u.extension.api.BackgroundTaskRequest
import com.m3u.extension.api.BackgroundTaskResult
import com.m3u.extension.api.ChannelMetadataPatch
import com.m3u.extension.api.EpgRefreshResult
import com.m3u.extension.api.ExtensionApiRange
import com.m3u.extension.api.ExtensionApiVersions
import com.m3u.extension.api.ExtensionCapabilityIds
import com.m3u.extension.api.ExtensionCapabilityRequest
import com.m3u.extension.api.ExtensionError
import com.m3u.extension.api.ExtensionErrorCode
import com.m3u.extension.api.ExtensionHookDeclaration
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.ExtensionManifest
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
import com.m3u.extension.api.SearchProviderResult
import com.m3u.extension.api.SettingsSchemaResult
import com.m3u.extension.api.security.BrokerAuthenticationRequest
import com.m3u.extension.api.security.BrokerAuthenticationResponse
import com.m3u.extension.api.security.BrokerHttpExchange
import com.m3u.extension.api.security.OpaqueContextCapture
import com.m3u.extension.api.security.ResponseValueSource
import com.m3u.extension.api.security.BrokerValue
import com.m3u.extension.api.security.BrokerValueEncoding
import com.m3u.extension.api.security.BrokeredHttpRequest
import com.m3u.extension.api.security.BrokeredHttpResponse
import com.m3u.extension.api.security.CredentialHandle
import com.m3u.extension.api.security.ContextReference
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
import com.m3u.extension.api.subscription.SubscriptionProviderSettingKeys
import com.m3u.extension.api.subscription.SubscriptionProviderVariant
import com.m3u.extension.api.subscription.SubscriptionProviderValidateRequest
import com.m3u.extension.api.subscription.SubscriptionProviderValidateResult
import com.m3u.extension.api.subscription.SubscriptionSourceDescriptor
import com.m3u.extension.sdk.android.ExtensionHostNetworkBroker
import com.m3u.extension.sdk.android.BrokerException
import com.m3u.extension.sdk.android.TypedExtensionService
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

class ReferenceExtensionService : TypedExtensionService() {
    override val extensionManifest: ExtensionManifest = REFERENCE_MANIFEST

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
            providerBrokerResult("playback resolution") { resolvePlayback(request, broker) }
        }
        handleResultWithBroker(SubscriptionHookSpecs.ClosePlayback) { request, _, broker ->
            providerBrokerResult("playback close") { closePlayback(request, broker) }
        }
        handle(HostHookSpecs.SearchProvider) { request, _ ->
            SearchProviderResult(
                items = listOf(
                    SearchProviderItem(
                        stableReference = request.query,
                        title = "Reference result for ${request.query}",
                        subtitle = if (request.query == "large") {
                            "x".repeat(LARGE_RESULT_SIZE)
                        } else {
                            "Reference extension search result"
                        },
                    )
                )
            )
        }
        handleWithBroker(HostHookSpecs.BackgroundTask) { request, context, broker ->
            runBackgroundTask(context.invocationId, request, context.settings, broker)
        }
        handle(HostHookSpecs.MetadataEnrichment) { request, _ ->
            MetadataEnrichmentResult(
                patches = request.channels.map { channel ->
                    ChannelMetadataPatch(
                        stableReference = channel.stableReference,
                        title = channel.title.takeIf { it.startsWith("unenriched:") }
                            ?.removePrefix("unenriched:"),
                        metadata = mapOf("reference-extension" to "true"),
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
                        metadata = mapOf("categories" to "Reference,Conformance"),
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

private fun discoverProvider(): SubscriptionProviderDiscoverResult =
    SubscriptionProviderDiscoverResult(
        providers = listOf(
            SubscriptionProviderDescriptor(
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
): HookResult<SubscriptionContentRefreshResult> {
    request.requireReferenceAccount()
    val response = broker.execute(request.referenceRefreshCall())
    response.providerFailure("channel_refresh")?.let { return it }
    val payload = response.decodeSuccess<ReferenceChannelsResponse>("channel refresh")
    return HookResult.Success(
        SubscriptionContentRefreshResult(
            source = SubscriptionSourceDescriptor(
                remoteId = request.account.serverId,
                title = payload.sourceTitle,
                providerKind = REFERENCE_PROVIDER_KIND,
            ),
            channels = payload.channels.map { channel ->
                SubscriptionChannelDescriptor(
                    remoteId = channel.id,
                    title = channel.title,
                    logoUrl = channel.logoUrl,
                    category = channel.category,
                    playbackReference = PlaybackReference(
                        providerId = REFERENCE_EXTENSION_ID,
                        itemId = channel.id,
                        sourceType = "live",
                    ),
                    epgReference = channel.epgReference,
                    metadata = mapOf("reference-fixture" to "true"),
                )
            },
            syncMetadata = mapOf("revision" to payload.revision),
        )
    )
}

private suspend fun resolvePlayback(
    request: PlaybackSourceResolveRequest,
    broker: ExtensionHostNetworkBroker,
): HookResult<PlaybackSourceResolveResult> {
    request.account.requireReferenceAccount()
    require(request.reference.providerId == REFERENCE_EXTENSION_ID) {
        "Playback reference belongs to another provider"
    }
    val response = broker.execute(request.referencePlaybackCall())
    response.providerFailure("playback_resolution")?.let { return it }
    val payload = response.decodeSuccess<ReferencePlaybackResponse>("playback resolution")
    return HookResult.Success(
        PlaybackSourceResolveResult(
            url = payload.url,
            headers = referencePlaybackHeaders(request.credential.handle),
            mediaSourceId = payload.mediaSourceId,
            session = PlaybackSessionDescriptor(
                playSessionId = payload.playSessionId,
                liveStreamId = payload.liveStreamId,
            ),
        )
    )
}

private suspend fun closePlayback(
    request: PlaybackSessionCloseRequest,
    broker: ExtensionHostNetworkBroker,
): HookResult<PlaybackSessionCloseResult> {
    request.account.requireReferenceAccount()
    require(request.reference.providerId == REFERENCE_EXTENSION_ID) {
        "Playback reference belongs to another provider"
    }
    val response = broker.execute(request.referenceCloseCall())
    response.providerFailure("playback_close")?.let { return it }
    val payload = response.decodeSuccess<ReferenceCloseResponse>("playback close")
    return HookResult.Success(PlaybackSessionCloseResult(closed = payload.closed))
}

private suspend fun runBackgroundTask(
    invocationId: InvocationId,
    request: BackgroundTaskRequest,
    settings: ExtensionSettingsSnapshot,
    broker: ExtensionHostNetworkBroker,
): BackgroundTaskResult {
    if (request.taskId == BROKER_PROBE_REASON) {
        val response = broker.execute(
            BrokeredHttpRequest(
                method = "GET",
                url = "https://reference.invalid/probe",
            )
        )
        return BackgroundTaskResult(
            output = mapOf(
                "status" to response.statusCode.toString(),
                "body" to response.body,
            )
        )
    }
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

private fun SubscriptionContentRefreshRequest.requireReferenceAccount() {
    account.requireReferenceAccount()
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

internal fun SubscriptionContentRefreshRequest.referenceRefreshCall(): BrokeredHttpRequest =
    BrokeredHttpRequest(
        method = "GET",
        url = account.baseUrl.referenceEndpoint("channels"),
        headers = referenceTokenHeader(credential.handle) + mapOf(
            REFERENCE_USER_HEADER to BrokerValue.Context(
                ContextReference(ProviderAuthenticationContextKeys.UserId)
            )
        ),
    )

internal fun PlaybackSourceResolveRequest.referencePlaybackCall(): BrokeredHttpRequest =
    BrokeredHttpRequest(
        method = "GET",
        url = account.baseUrl.referenceEndpoint(
            "playback/${reference.itemId.pathSegment()}"
        ),
        headers = referenceTokenHeader(credential.handle),
    )

internal fun PlaybackSessionCloseRequest.referenceCloseCall(): BrokeredHttpRequest {
    val body = referenceJson.encodeToString(
        ReferenceCloseRequest(
            itemId = reference.itemId,
            playSessionId = requireNotNull(session.playSessionId) {
                "Reference playback session id is required"
            },
            liveStreamId = requireNotNull(session.liveStreamId) {
                "Reference live stream id is required"
            },
            reason = reason.value,
        )
    )
    return BrokeredHttpRequest(
        method = "POST",
        url = account.baseUrl.referenceEndpoint("sessions/close"),
        headers = mapOf(
            "Content-Type" to BrokerValue.Literal("application/json"),
        ) + referenceTokenHeader(credential.handle),
        body = listOf(BrokerValue.Literal(body)),
    )
}

internal fun referencePlaybackHeaders(
    credential: CredentialHandle,
): Map<String, PlaybackHeaderValue> = mapOf(
    REFERENCE_TOKEN_HEADER to PlaybackHeaderValue(
        parts = listOf(BrokerValue.Secret(SecretReference(credential)))
    )
)

private fun referenceTokenHeader(
    credential: CredentialHandle,
): Map<String, BrokerValue> = mapOf(
    REFERENCE_TOKEN_HEADER to BrokerValue.Secret(SecretReference(credential))
)

private fun ProviderAccountReference.requireReferenceAccount() {
    require(providerId == REFERENCE_EXTENSION_ID) {
        "Provider account belongs to another extension"
    }
    require(providerKind == REFERENCE_PROVIDER_KIND) {
        "Provider account has an unsupported kind"
    }
}

private inline fun <reified T> BrokeredHttpResponse.decodeSuccess(operation: String): T {
    check(statusCode in 200..299) {
        "Reference provider $operation failed with HTTP $statusCode"
    }
    return referenceJson.decodeFromString(body)
}

private fun BrokeredHttpResponse.providerFailure(operation: String): HookResult.Failure? =
    statusCode.providerFailure(operation)

private fun BrokerAuthenticationResponse.providerFailure(operation: String): HookResult.Failure? =
    statusCode.providerFailure(operation)

private fun Int.providerFailure(operation: String): HookResult.Failure? {
    if (this in 200..299) return null
    val (code, message) = when (this) {
        401, 403 -> "provider.authentication_failed" to "Provider credentials were rejected"
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

private fun String.pathSegment(): String =
    URLEncoder.encode(this, StandardCharsets.UTF_8.name()).replace("+", "%20")

@Serializable
private data class ReferenceChannelsResponse(
    @SerialName("source_id") val sourceId: String,
    @SerialName("source_title") val sourceTitle: String,
    val revision: String,
    val channels: List<ReferenceChannel>,
)

@Serializable
private data class ReferenceChannel(
    val id: String,
    val title: String,
    val category: String,
    @SerialName("logo_url") val logoUrl: String? = null,
    @SerialName("epg_reference") val epgReference: String? = null,
)

@Serializable
private data class ReferencePlaybackResponse(
    val url: String,
    @SerialName("media_source_id") val mediaSourceId: String,
    @SerialName("play_session_id") val playSessionId: String,
    @SerialName("live_stream_id") val liveStreamId: String,
)

@Serializable
private data class ReferenceCloseRequest(
    @SerialName("item_id") val itemId: String,
    @SerialName("play_session_id") val playSessionId: String,
    @SerialName("live_stream_id") val liveStreamId: String,
    val reason: String,
)

@Serializable
private data class ReferenceCloseResponse(
    val closed: Boolean,
)

private val referenceJson = Json {
    ignoreUnknownKeys = false
    explicitNulls = false
}

internal val REFERENCE_EXTENSION_ID = ExtensionId("com.m3u.reference.provider")
internal val REFERENCE_PROVIDER_KIND = ProviderKind("reference")
private const val REFERENCE_TOKEN_HEADER = "X-Emby-Token"
private const val REFERENCE_USER_HEADER = "X-Reference-User"
private const val BROKER_PROBE_REASON = "broker-probe"
private const val LARGE_RESULT_SIZE = 1_200_000
private val lastCancelled = AtomicReference<String?>(null)

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
                ExtensionCapabilityIds.Network,
                ExtensionCapabilityIds.CredentialRead,
                ExtensionCapabilityIds.SubscriptionRead,
            ),
        ),
        ExtensionHookDeclaration(
            hook = SubscriptionHookSpecs.ResolvePlayback.hook,
            schemaVersion = SubscriptionHookSpecs.ResolvePlayback.schemaVersion,
            requiredCapabilities = setOf(
                ExtensionCapabilityIds.Network,
                ExtensionCapabilityIds.CredentialRead,
                ExtensionCapabilityIds.PlaybackResolve,
            ),
        ),
        ExtensionHookDeclaration(
            hook = SubscriptionHookSpecs.ClosePlayback.hook,
            schemaVersion = SubscriptionHookSpecs.ClosePlayback.schemaVersion,
            requiredCapabilities = setOf(
                ExtensionCapabilityIds.Network,
                ExtensionCapabilityIds.CredentialRead,
                ExtensionCapabilityIds.PlaybackResolve,
            ),
        ),
        ExtensionHookDeclaration(
            hook = HostHookSpecs.SearchProvider.hook,
            schemaVersion = HostHookSpecs.SearchProvider.schemaVersion,
            requiredCapabilities = setOf(ExtensionCapabilityIds.SearchRead),
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
            "Authenticate reference provider requests",
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
)
