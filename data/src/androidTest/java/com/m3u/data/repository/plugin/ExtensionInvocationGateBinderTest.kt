package com.m3u.data.repository.plugin

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.m3u.core.foundation.architecture.preferences.PreferencesKeys
import com.m3u.core.foundation.architecture.preferences.settings
import com.m3u.data.extension.security.ActiveExtensionPrincipalRegistry
import com.m3u.data.extension.security.ExtensionPrincipal
import com.m3u.data.extension.security.ProviderHostNetworkBroker
import com.m3u.data.extension.security.toPrincipal
import com.m3u.extension.api.ChannelMetadataSnapshot
import com.m3u.extension.api.ExtensionApiVersions
import com.m3u.extension.api.ExtensionCapabilityIds
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.HostHookSpecs
import com.m3u.extension.api.Hook
import com.m3u.extension.api.InvocationId
import com.m3u.extension.api.MetadataEnrichmentRequest
import com.m3u.extension.api.SerializedExtensionEnvelope
import com.m3u.extension.api.security.BrokerScopeHandle
import com.m3u.extension.api.security.BrokeredHttpRequest
import com.m3u.extension.api.security.BrokeredHttpResponse
import com.m3u.extension.transport.android.AndroidExtensionDiscovery
import com.m3u.extension.transport.android.ExtensionTrustStore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExtensionInvocationGateBinderTest {
    @Test
    fun killSwitchStopsInvocationBeforeTheBoundServiceReceivesBinderCall() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("extension-trust", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        context.settings.edit { preferences ->
            preferences[PreferencesKeys.EXTERNAL_EXTENSIONS] = true
        }
        val service = AndroidExtensionDiscovery(context).discover().single { candidate ->
            candidate.packageName == REFERENCE_EXTENSION_PACKAGE &&
                candidate.serviceName == REFERENCE_EXTENSION_SERVICE
        }
        assertNull(service.incompatibilityReason)
        val trustStore = ExtensionTrustStore(context)
        val principalRegistry = ActiveExtensionPrincipalRegistry()
        val connector = AndroidExtensionPluginTransportConnector(
            context = context,
            hostNetworkBroker = UnexpectedNetworkBroker,
            invocationGate = ExtensionInvocationGate(
                settings = context.settings,
                trustStore = trustStore,
                principalRegistry = principalRegistry,
            ),
        )
        val transport = connector.connect(service)
        try {
            val manifest = transport.manifest
            assertEquals(REFERENCE_EXTENSION_ID, manifest.id)
            trustStore.trust(
                service = service,
                extensionId = manifest.id.value,
                capabilities = setOf(ExtensionCapabilityIds.MetadataWrite.id),
                displayName = manifest.displayName,
                version = manifest.extensionVersion.toString(),
                developer = manifest.metadata["developer"],
            )
            principalRegistry.activate(service.toPrincipal(manifest.id))

            assertEquals(0, transport.probeCount("reset", PROBE_RESET_REFERENCE))
            assertEquals(1, transport.probeCount("allowed", PROBE_COUNT_REFERENCE))

            context.settings.edit { preferences ->
                preferences[PreferencesKeys.EXTERNAL_EXTENSIONS] = false
            }
            val failure = runCatching {
                transport.invoke(probeEnvelope("blocked", PROBE_COUNT_REFERENCE))
            }.exceptionOrNull()

            assertTrue(failure is SecurityException)
            context.settings.edit { preferences ->
                preferences[PreferencesKeys.EXTERNAL_EXTENSIONS] = true
            }
            assertEquals(2, transport.probeCount("after-block", PROBE_COUNT_REFERENCE))
        } finally {
            transport.close()
            context.settings.edit { preferences ->
                preferences[PreferencesKeys.EXTERNAL_EXTENSIONS] = true
            }
            context.getSharedPreferences("extension-trust", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()
        }
    }

    private fun probeEnvelope(
        invocationId: String,
        stableReference: String,
    ) = SerializedExtensionEnvelope(
        apiVersion = ExtensionApiVersions.Current,
        invocationId = InvocationId(invocationId),
        extensionId = REFERENCE_EXTENSION_ID,
        hook = HostHookSpecs.MetadataEnrichment.hook,
        schemaVersion = HostHookSpecs.MetadataEnrichment.schemaVersion,
        payload = Json.encodeToJsonElement(
            HostHookSpecs.MetadataEnrichment.requestSerializer,
            MetadataEnrichmentRequest(
                channels = listOf(
                    ChannelMetadataSnapshot(
                        stableReference = stableReference,
                        title = "probe",
                        category = "test",
                    )
                )
            ),
        ),
        grantedCapabilities = setOf(ExtensionCapabilityIds.MetadataWrite),
    )

    private suspend fun ExtensionPluginTransport.probeCount(
        invocationId: String,
        stableReference: String,
    ): Int {
        val result = invoke(probeEnvelope(invocationId, stableReference))
        val payload = Json.decodeFromJsonElement(
            HostHookSpecs.MetadataEnrichment.responseSerializer,
            checkNotNull(result.payload),
        )
        return checkNotNull(payload.patches.single().title)
            .removePrefix(PROBE_COUNT_TITLE_PREFIX)
            .toInt()
    }
}

private object UnexpectedNetworkBroker : ProviderHostNetworkBroker {
    override suspend fun execute(
        scope: BrokerScopeHandle,
        principal: ExtensionPrincipal,
        hook: Hook,
        request: BrokeredHttpRequest,
    ): BrokeredHttpResponse = error("Network broker must not be called")
}

private val REFERENCE_EXTENSION_ID = ExtensionId("com.m3u.reference.provider")
private const val REFERENCE_EXTENSION_PACKAGE = "com.m3u.testing.extension.reference"
private const val REFERENCE_EXTENSION_SERVICE =
    "$REFERENCE_EXTENSION_PACKAGE.ReferenceExtensionService"
private const val PROBE_RESET_REFERENCE = "test.invocation-gate.reset"
private const val PROBE_COUNT_REFERENCE = "test.invocation-gate.count"
private const val PROBE_COUNT_TITLE_PREFIX = "invocation-count:"
