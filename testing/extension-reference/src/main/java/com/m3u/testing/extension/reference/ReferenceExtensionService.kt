package com.m3u.testing.extension.reference

import com.m3u.extension.api.ExtensionApiRange
import com.m3u.extension.api.ExtensionApiVersions
import com.m3u.extension.api.ExtensionHookDeclaration
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.ExtensionManifest
import com.m3u.extension.api.ExtensionSemanticVersion
import com.m3u.extension.api.InvocationId
import com.m3u.extension.api.SerializedExtensionEnvelope
import com.m3u.extension.api.SerializedExtensionResult
import com.m3u.extension.api.subscription.ProviderKind
import com.m3u.extension.api.subscription.SubscriptionHookSpecs
import com.m3u.extension.api.subscription.SubscriptionProviderDescriptor
import com.m3u.extension.api.subscription.SubscriptionProviderDiscoverResult
import com.m3u.extension.runtime.ExtensionTransport
import com.m3u.extension.runtime.ExtensionTransportHealth
import com.m3u.extension.sdk.android.ExtensionService
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement

class ReferenceExtensionService : ExtensionService() {
    override val transport: ExtensionTransport = ReferenceTransport
}

private object ReferenceTransport : ExtensionTransport {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val extensionId = ExtensionId("com.m3u.reference.provider")

    override val manifest = ExtensionManifest(
        id = extensionId,
        displayName = "Reference Provider",
        extensionVersion = ExtensionSemanticVersion(1, 0, 0),
        apiRange = ExtensionApiRange(ExtensionApiVersions.Current, ExtensionApiVersions.Current),
        hooks = setOf(
            ExtensionHookDeclaration(
                hook = SubscriptionHookSpecs.Discover.hook,
                schemaVersion = SubscriptionHookSpecs.Discover.schemaVersion,
            )
        ),
        capabilities = emptySet(),
        metadata = mapOf("developer" to "M3U Conformance Suite"),
    )

    override suspend fun invoke(request: SerializedExtensionEnvelope): SerializedExtensionResult {
        require(request.hook == SubscriptionHookSpecs.Discover.hook)
        val response = SubscriptionProviderDiscoverResult(
            providers = listOf(
                SubscriptionProviderDescriptor(
                    providerId = extensionId,
                    displayName = "Reference Provider",
                    supportedKinds = setOf(ProviderKind("reference")),
                )
            )
        )
        return SerializedExtensionResult(
            invocationId = request.invocationId,
            extensionId = extensionId,
            hook = request.hook,
            schemaVersion = request.schemaVersion,
            payload = json.encodeToJsonElement(
                SubscriptionHookSpecs.Discover.responseSerializer,
                response,
            ),
        )
    }

    override suspend fun cancel(invocationId: InvocationId) = Unit
    override suspend fun health(): ExtensionTransportHealth = ExtensionTransportHealth.HEALTHY
}
