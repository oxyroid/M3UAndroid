package com.m3u.testing.extension.reference

import com.m3u.extension.api.ExtensionApiRange
import com.m3u.extension.api.ExtensionApiVersions
import com.m3u.extension.api.BackgroundTaskRequest
import com.m3u.extension.api.BackgroundTaskResult
import com.m3u.extension.api.ExtensionCapabilityIds
import com.m3u.extension.api.ExtensionCapabilityRequest
import com.m3u.extension.api.ExtensionHookDeclaration
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.ExtensionManifest
import com.m3u.extension.api.ExtensionSemanticVersion
import com.m3u.extension.api.InvocationId
import com.m3u.extension.api.SerializedExtensionEnvelope
import com.m3u.extension.api.SerializedExtensionResult
import com.m3u.extension.api.HostHookSpecs
import com.m3u.extension.api.SearchProviderItem
import com.m3u.extension.api.SearchProviderResult
import com.m3u.extension.api.subscription.ProviderKind
import com.m3u.extension.api.subscription.SubscriptionHookSpecs
import com.m3u.extension.api.subscription.SubscriptionProviderDescriptor
import com.m3u.extension.api.subscription.SubscriptionProviderDiscoverResult
import com.m3u.extension.runtime.ExtensionTransport
import com.m3u.extension.runtime.ExtensionTransportHealth
import com.m3u.extension.sdk.android.ExtensionService
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

class ReferenceExtensionService : ExtensionService() {
    override val transport: ExtensionTransport = ReferenceTransport
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
        ),
        capabilities = setOf(
            ExtensionCapabilityRequest(
                ExtensionCapabilityIds.SearchRead,
                "Return reference search results",
            ),
            ExtensionCapabilityRequest(
                ExtensionCapabilityIds.BackgroundTask,
                "Exercise cancellation conformance",
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
            HostHookSpecs.SearchProvider.hook -> json.encodeToJsonElement(
                HostHookSpecs.SearchProvider.responseSerializer,
                SearchProviderResult(
                    items = listOf(
                        SearchProviderItem(
                            stableReference = "reference://large-result",
                            title = "Large reference result",
                            subtitle = "x".repeat(LARGE_RESULT_SIZE),
                        )
                    )
                )
            )
            HostHookSpecs.BackgroundTask.hook -> {
                val input = json.decodeFromJsonElement(
                    HostHookSpecs.BackgroundTask.requestSerializer,
                    request.payload,
                )
                json.encodeToJsonElement(
                    HostHookSpecs.BackgroundTask.responseSerializer,
                    runBackgroundTask(request.invocationId, input),
                )
            }
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
    ): BackgroundTaskResult {
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
