package com.m3u.testing.extension.reference

import com.m3u.extension.api.ExtensionApiRange
import com.m3u.extension.api.ExtensionApiVersions
import com.m3u.extension.api.BackgroundTaskRequest
import com.m3u.extension.api.BackgroundTaskResult
import com.m3u.extension.api.ChannelMetadataPatch
import com.m3u.extension.api.ExtensionCapabilityIds
import com.m3u.extension.api.ExtensionCapabilityRequest
import com.m3u.extension.api.ExtensionHookDeclaration
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.ExtensionManifest
import com.m3u.extension.api.ExtensionProgramme
import com.m3u.extension.api.ExtensionSettingChoice
import com.m3u.extension.api.ExtensionSettingField
import com.m3u.extension.api.ExtensionSettingSchema
import com.m3u.extension.api.ExtensionSettingSection
import com.m3u.extension.api.ExtensionSettingsSnapshot
import com.m3u.extension.api.ExtensionSettingType
import com.m3u.extension.api.ExtensionSemanticVersion
import com.m3u.extension.api.InvocationId
import com.m3u.extension.api.MetadataEnrichmentResult
import com.m3u.extension.api.EpgRefreshResult
import com.m3u.extension.api.SettingsSchemaResult
import com.m3u.extension.api.SerializedExtensionEnvelope
import com.m3u.extension.api.SerializedExtensionResult
import com.m3u.extension.api.HostHookSpecs
import com.m3u.extension.api.SearchProviderItem
import com.m3u.extension.api.SearchProviderResult
import com.m3u.extension.api.security.BrokeredHttpRequest
import com.m3u.extension.api.subscription.ProviderKind
import com.m3u.extension.api.subscription.SubscriptionHookSpecs
import com.m3u.extension.api.subscription.SubscriptionProviderDescriptor
import com.m3u.extension.api.subscription.SubscriptionProviderDiscoverResult
import com.m3u.extension.runtime.ExtensionTransport
import com.m3u.extension.runtime.ExtensionTransportHealth
import com.m3u.extension.sdk.android.ExtensionHostNetworkBroker
import com.m3u.extension.sdk.android.ExtensionService
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

class ReferenceExtensionService : ExtensionService() {
    override val transport: ExtensionTransport = ReferenceTransport

    override suspend fun invoke(
        envelope: SerializedExtensionEnvelope,
        hostNetworkBroker: ExtensionHostNetworkBroker,
    ): SerializedExtensionResult {
        if (envelope.hook == HostHookSpecs.BackgroundTask.hook) {
            val request = json.decodeFromJsonElement(
                HostHookSpecs.BackgroundTask.requestSerializer,
                envelope.payload,
            )
            if (request.taskId == BROKER_PROBE_REASON) {
                val response = hostNetworkBroker.execute(
                    accountId = "reference-account",
                    request = BrokeredHttpRequest(
                        method = "GET",
                        url = "https://reference.invalid/probe",
                    ),
                )
                return SerializedExtensionResult(
                    invocationId = envelope.invocationId,
                    extensionId = envelope.extensionId,
                    hook = envelope.hook,
                    schemaVersion = envelope.schemaVersion,
                    payload = json.encodeToJsonElement(
                        HostHookSpecs.BackgroundTask.responseSerializer,
                        BackgroundTaskResult(
                            output = mapOf(
                                "status" to response.statusCode.toString(),
                                "body" to response.body,
                            )
                        ),
                    ),
                )
            }
        }
        return super.invoke(envelope, hostNetworkBroker)
    }

    private companion object {
        const val BROKER_PROBE_REASON = "broker-probe"
    }
}

private object ReferenceTransport : ExtensionTransport {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val extensionId = ExtensionId("com.m3u.reference.provider")
    private val invocations = ConcurrentHashMap<InvocationId, Job>()
    private val lastCancelled = AtomicReference<String?>(null)

    override val manifest = ExtensionManifest(
        id = extensionId,
        displayName = "Reference Provider",
        extensionVersion = ExtensionSemanticVersion(1, 0, 0),
        apiRange = ExtensionApiRange(ExtensionApiVersions.Current, ExtensionApiVersions.Current),
        hooks = setOf(
            ExtensionHookDeclaration(
                hook = SubscriptionHookSpecs.Discover.hook,
                schemaVersion = SubscriptionHookSpecs.Discover.schemaVersion,
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
                "Exercise the invocation-scoped host bridge",
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

    override suspend fun invoke(request: SerializedExtensionEnvelope): SerializedExtensionResult {
        val payload = when (request.hook) {
            SubscriptionHookSpecs.Discover.hook -> json.encodeToJsonElement(
                SubscriptionHookSpecs.Discover.responseSerializer,
                SubscriptionProviderDiscoverResult(
                    providers = listOf(
                        SubscriptionProviderDescriptor(
                            providerId = extensionId,
                            displayName = "Reference Provider",
                            supportedKinds = setOf(ProviderKind("reference")),
                        )
                    )
                )
            )
            HostHookSpecs.SearchProvider.hook -> {
                val input = json.decodeFromJsonElement(
                    HostHookSpecs.SearchProvider.requestSerializer,
                    request.payload,
                )
                json.encodeToJsonElement(
                    HostHookSpecs.SearchProvider.responseSerializer,
                    SearchProviderResult(
                        items = listOf(
                            SearchProviderItem(
                                stableReference = input.query,
                                title = "Reference result for ${input.query}",
                                subtitle = if (input.query == "large") {
                                    "x".repeat(LARGE_RESULT_SIZE)
                                } else {
                                    "Reference extension search result"
                                },
                            )
                        )
                    )
                )
            }
            HostHookSpecs.BackgroundTask.hook -> {
                val input = json.decodeFromJsonElement(
                    HostHookSpecs.BackgroundTask.requestSerializer,
                    request.payload,
                )
                json.encodeToJsonElement(
                    HostHookSpecs.BackgroundTask.responseSerializer,
                    runBackgroundTask(request.invocationId, input, request.settings),
                )
            }
            HostHookSpecs.MetadataEnrichment.hook -> {
                val input = json.decodeFromJsonElement(
                    HostHookSpecs.MetadataEnrichment.requestSerializer,
                    request.payload,
                )
                json.encodeToJsonElement(
                    HostHookSpecs.MetadataEnrichment.responseSerializer,
                    MetadataEnrichmentResult(
                        patches = input.channels.map { channel ->
                            ChannelMetadataPatch(
                                stableReference = channel.stableReference,
                                title = channel.title.takeIf { it.startsWith("unenriched:") }
                                    ?.removePrefix("unenriched:"),
                                metadata = mapOf("reference-extension" to "true"),
                            )
                        }
                    ),
                )
            }
            HostHookSpecs.EpgRefresh.hook -> {
                val input = json.decodeFromJsonElement(
                    HostHookSpecs.EpgRefresh.requestSerializer,
                    request.payload,
                )
                json.encodeToJsonElement(
                    HostHookSpecs.EpgRefresh.responseSerializer,
                    EpgRefreshResult(
                        programmes = input.sourceIds.map { reference ->
                            ExtensionProgramme(
                                channelReference = reference,
                                title = "Reference programme",
                                startEpochMillis = input.fromEpochMillis,
                                endEpochMillis = input.toEpochMillis,
                                metadata = mapOf("categories" to "Reference,Conformance"),
                            )
                        }
                    ),
                )
            }
            HostHookSpecs.SettingsSchema.hook -> json.encodeToJsonElement(
                HostHookSpecs.SettingsSchema.responseSerializer,
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
                ),
            )
            else -> error("Unsupported reference hook: ${request.hook}")
        }
        return SerializedExtensionResult(
            invocationId = request.invocationId,
            extensionId = extensionId,
            hook = request.hook,
            schemaVersion = request.schemaVersion,
            payload = payload,
        )
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
            return BackgroundTaskResult(mapOf("cancelled" to (lastCancelled.get() != null).toString()))
        }
        val job = checkNotNull(currentCoroutineContext()[Job])
        invocations[invocationId] = job
        return try {
            delay(10_000)
            BackgroundTaskResult(mapOf("completed" to "true"))
        } finally {
            invocations.remove(invocationId)
        }
    }

    override suspend fun cancel(invocationId: InvocationId) {
        lastCancelled.set(invocationId.value)
        invocations[invocationId]?.cancel()
    }
    override suspend fun health(): ExtensionTransportHealth = ExtensionTransportHealth.HEALTHY

    private const val LARGE_RESULT_SIZE = 1_200_000
}
