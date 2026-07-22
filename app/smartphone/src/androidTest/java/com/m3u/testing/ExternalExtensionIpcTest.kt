package com.m3u.testing

import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import com.m3u.extension.api.BackgroundTaskRequest
import com.m3u.extension.api.BackgroundTaskResult
import com.m3u.extension.api.ChannelMetadataSnapshot
import com.m3u.extension.api.EpgRefreshRequest
import com.m3u.extension.api.EpgRefreshResult
import com.m3u.extension.api.ExtensionApiVersions
import com.m3u.extension.api.ExtensionSettingsSnapshot
import com.m3u.extension.api.HookResult
import com.m3u.extension.api.HostHookSpecs
import com.m3u.extension.api.MetadataEnrichmentRequest
import com.m3u.extension.api.MetadataEnrichmentResult
import com.m3u.extension.api.SearchProviderRequest
import com.m3u.extension.api.SearchProviderResult
import com.m3u.extension.api.SettingsSchemaRequest
import com.m3u.extension.api.SettingsSchemaResult
import com.m3u.extension.api.security.BrokerInvocation
import com.m3u.extension.api.security.BrokerInvocationResult
import com.m3u.extension.api.security.BrokerOperation
import com.m3u.extension.api.security.BrokerOperationResult
import com.m3u.extension.api.security.BrokerProtocolVersions
import com.m3u.extension.api.security.BrokerValue
import com.m3u.extension.api.security.BrokeredHttpResponse
import com.m3u.extension.api.security.CredentialHandle
import com.m3u.extension.api.subscription.SubscriptionHookSpecs
import com.m3u.extension.api.subscription.SubscriptionProviderDiscoverRequest
import com.m3u.extension.api.subscription.SubscriptionProviderDiscoverResult
import com.m3u.extension.runtime.ExtensionRegistrationResult
import com.m3u.extension.runtime.ExtensionRuntime
import com.m3u.extension.runtime.ExtensionSettingsProvider
import com.m3u.extension.runtime.InvocationPolicy
import com.m3u.extension.transport.android.AndroidBoundExtensionTransport
import com.m3u.extension.transport.android.AndroidExtensionDiscovery
import com.m3u.extension.transport.android.ParcelFileCodec
import com.m3u.extension.transport.android.ipc.IExtensionHostBridge
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalExtensionIpcTest {
    @Test
    fun referenceApkIsDiscoveredBoundAndInvokedThroughStreamTransport() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val installed = AndroidExtensionDiscovery(context).discover().firstOrNull {
            it.packageName == REFERENCE_PACKAGE
        } ?: error("Reference extension APK was not installed by the conformance task")
        val transport = AndroidBoundExtensionTransport.connect(
            context = context,
            installed = installed,
            hostBridgeFactory = { _, _ -> ConformanceHostBridge },
        )
        try {
            val runtime = ExtensionRuntime(
                ExtensionApiVersions.Current,
                invocationPolicy = InvocationPolicy(maxPayloadBytes = 2_000_000),
                settingsProvider = ExtensionSettingsProvider {
                    ExtensionSettingsSnapshot(
                        schemaVersions = mapOf("manifest" to 1),
                        values = mapOf("manifest/enabled" to JsonPrimitive(false)),
                        credentialHandles = mapOf(
                            "manifest/api-key" to CredentialHandle("extension-secret:test"),
                        ),
                    )
                },
            )
            assertTrue(runtime.register(transport) is ExtensionRegistrationResult.Registered)

            val result = runtime.invoke(
                extensionId = transport.manifest.id,
                spec = SubscriptionHookSpecs.Discover,
                request = SubscriptionProviderDiscoverRequest(),
            )
            val payload = (result.outcome as HookResult.Success<*>).payload as
                SubscriptionProviderDiscoverResult

            assertEquals("Reference Provider", payload.providers.single().displayName)
            assertEquals("Reference", payload.providers.single().variants.single().displayName)
            assertEquals("reference", payload.providers.single().variants.single().kind.value)

            val largeResult = runtime.invoke(
                extensionId = transport.manifest.id,
                spec = HostHookSpecs.SearchProvider,
                request = SearchProviderRequest("large"),
            )
            val search = (largeResult.outcome as HookResult.Success<*>).payload as SearchProviderResult
            assertEquals(1_200_000, search.items.single().subtitle?.length)

            val metadataResult = runtime.invoke(
                extensionId = transport.manifest.id,
                spec = HostHookSpecs.MetadataEnrichment,
                request = MetadataEnrichmentRequest(
                    channels = listOf(
                        ChannelMetadataSnapshot(
                            stableReference = "channel-42",
                            title = "unenriched:Reference Channel",
                            category = "Reference",
                        )
                    )
                ),
            )
            val metadata = (metadataResult.outcome as HookResult.Success<*>).payload as
                MetadataEnrichmentResult
            assertEquals("channel-42", metadata.patches.single().stableReference)
            assertEquals("Reference Channel", metadata.patches.single().title)

            val epgResult = runtime.invoke(
                extensionId = transport.manifest.id,
                spec = HostHookSpecs.EpgRefresh,
                request = EpgRefreshRequest(
                    sourceIds = listOf("channel-42"),
                    fromEpochMillis = 1_000,
                    toEpochMillis = 2_000,
                ),
            )
            val epg = (epgResult.outcome as HookResult.Success<*>).payload as EpgRefreshResult
            assertEquals("channel-42", epg.programmes.single().channelReference)
            assertEquals("Reference programme", epg.programmes.single().title)

            val settingsResult = runtime.invoke(
                extensionId = transport.manifest.id,
                spec = HostHookSpecs.SettingsSchema,
                request = SettingsSchemaRequest(localeTag = "en-US", surface = "phone"),
            )
            val settings = (settingsResult.outcome as HookResult.Success<*>).payload as
                SettingsSchemaResult
            assertEquals("playback", settings.sections.single().id)

            val settingsStatus = runtime.invoke(
                extensionId = transport.manifest.id,
                spec = HostHookSpecs.BackgroundTask,
                request = BackgroundTaskRequest("settings-status"),
            )
            val settingsOutput = (settingsStatus.outcome as HookResult.Success<*>).payload as
                BackgroundTaskResult
            assertEquals("false", settingsOutput.output["enabled"])
            assertEquals("true", settingsOutput.output["hasApiKey"])

            val brokerProbe = runtime.invoke(
                extensionId = transport.manifest.id,
                spec = HostHookSpecs.BackgroundTask,
                request = BackgroundTaskRequest("broker-probe"),
            )
            val brokerOutcome = brokerProbe.outcome
            check(brokerOutcome is HookResult.Success<*>) {
                val failure = brokerOutcome as HookResult.Failure
                "Broker probe failed: ${failure.error.code.value} ${failure.error.message} " +
                    failure.error.details
            }
            val brokerOutput = brokerOutcome.payload as BackgroundTaskResult
            assertEquals("204", brokerOutput.output["status"])
            assertEquals("caller-bound", brokerOutput.output["body"])

            val slowInvocation = async {
                runtime.invoke(
                    extensionId = transport.manifest.id,
                    spec = HostHookSpecs.BackgroundTask,
                    request = BackgroundTaskRequest("slow"),
                )
            }
            delay(200)
            slowInvocation.cancel()
            runCatching { slowInvocation.await() }
            delay(200)
            val cancelStatus = runtime.invoke(
                extensionId = transport.manifest.id,
                spec = HostHookSpecs.BackgroundTask,
                request = BackgroundTaskRequest("cancel-status"),
            )
            val status = (cancelStatus.outcome as HookResult.Success<*>).payload as
                BackgroundTaskResult
            assertEquals("true", status.output["cancelled"])
        } finally {
            transport.close()
        }
    }

    private object ConformanceHostBridge : IExtensionHostBridge.Stub() {
        private val json = Json { ignoreUnknownKeys = true }

        override fun executeHttp(request: ParcelFileDescriptor): ParcelFileDescriptor {
            val invocation = json.decodeFromString<BrokerInvocation>(
                ParcelFileCodec.read(request, 64 * 1024)
            )
            assertEquals(BrokerProtocolVersions.Current, invocation.brokerProtocolVersion)
            val operation = invocation.operation as BrokerOperation.Http
            assertEquals(
                BrokerValue.Literal("https://reference.invalid/probe"),
                operation.request.url,
            )
            val result: BrokerInvocationResult = BrokerInvocationResult.Success(
                BrokerOperationResult.Http(
                    BrokeredHttpResponse(
                        statusCode = 204,
                        headers = emptyMap(),
                        body = "caller-bound",
                    )
                )
            )
            return ParcelFileCodec.write(
                InstrumentationRegistry.getInstrumentation().targetContext,
                json.encodeToString(result),
            )
        }
    }

    private companion object {
        const val REFERENCE_PACKAGE = "com.m3u.testing.extension.reference"
    }
}
