package com.m3u.data.extension.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.m3u.extension.api.Capability
import com.m3u.extension.api.ExtensionApiRange
import com.m3u.extension.api.ExtensionApiVersion
import com.m3u.extension.api.ExtensionCapabilityIds
import com.m3u.extension.api.ExtensionCapabilityRequest
import com.m3u.extension.api.ExtensionHookDeclaration
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.ExtensionManifest
import com.m3u.extension.api.ExtensionSemanticVersion
import com.m3u.extension.api.Hook
import com.m3u.extension.api.InvocationId
import com.m3u.extension.api.SerializedExtensionEnvelope
import com.m3u.extension.api.security.BrokerInvocation
import com.m3u.extension.api.security.BrokerValue
import com.m3u.extension.api.security.BrokeredHttpRequest
import com.m3u.extension.api.security.BrokeredHttpResponse
import com.m3u.extension.api.security.CredentialHandle
import com.m3u.extension.api.security.HostNetworkBroker
import com.m3u.extension.api.security.SecretCaptureRule
import com.m3u.extension.api.security.SecretReference
import com.m3u.extension.transport.android.ParcelFileCodec
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExtensionHostBridgeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val json = Json { explicitNulls = false }

    @Test
    fun networkCapabilityIsCheckedByHostBeforeBrokerExecution() {
        val broker = RecordingBroker()
        val bridge = bridge(broker, granted = emptySet())

        expectThrows<SecurityException> {
            execute(bridge, BrokeredHttpRequest(method = "GET", url = "https://example.com"))
        }

        assertEquals(0, broker.calls)
    }

    @Test
    fun credentialReadAndWriteUseSeparateHostCapabilities() {
        val broker = RecordingBroker()
        val networkOnly = bridge(broker, granted = setOf(ExtensionCapabilityIds.Network))
        val secretRequest = BrokeredHttpRequest(
            method = "GET",
            url = "https://example.com",
            headers = mapOf(
                "Authorization" to BrokerValue.Secret(
                    SecretReference(CredentialHandle("extension-secret:test"))
                )
            ),
        )

        expectThrows<SecurityException> { execute(networkOnly, secretRequest) }

        val readOnly = bridge(
            broker,
            granted = setOf(
                ExtensionCapabilityIds.Network,
                ExtensionCapabilityIds.CredentialRead,
            ),
        )
        val captureRequest = BrokeredHttpRequest(
            method = "POST",
            url = "https://example.com/login",
            secretCapture = SecretCaptureRule.JsonPointer("/token"),
        )

        expectThrows<SecurityException> { execute(readOnly, captureRequest) }
        assertEquals(0, broker.calls)
    }

    @Test
    fun grantedInvocationDelegatesWithBoundExtensionIdentity() {
        val broker = RecordingBroker()
        val bridge = bridge(broker, granted = setOf(ExtensionCapabilityIds.Network))

        val response = execute(
            bridge,
            BrokeredHttpRequest(method = "GET", url = "https://example.com/channels"),
        )

        assertEquals(200, response.statusCode)
        assertEquals(EXTENSION_ID.value, broker.extensionId)
        assertEquals(1, broker.calls)
    }

    @Test
    fun invocationBridgeCannotBeReusedAfterClose() {
        val broker = RecordingBroker()
        val bridge = bridge(broker, granted = setOf(ExtensionCapabilityIds.Network))
        bridge.close()

        expectThrows<IllegalStateException> {
            execute(bridge, BrokeredHttpRequest(method = "GET", url = "https://example.com"))
        }

        assertEquals(0, broker.calls)
    }

    @Test
    fun closingInvocationBridgeCancelsAnInFlightBrokerRequest() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val broker = object : HostNetworkBroker {
            override suspend fun execute(
                extensionId: String,
                accountId: String,
                request: BrokeredHttpRequest,
            ): BrokeredHttpResponse {
                started.complete(Unit)
                awaitCancellation()
            }
        }
        val bridge = ExtensionHostBridge(
            context = context,
            broker = broker,
            manifest = MANIFEST,
            envelope = envelope(granted = setOf(ExtensionCapabilityIds.Network)),
        )
        val execution = async(Dispatchers.IO) {
            runCatching {
                execute(bridge, BrokeredHttpRequest(method = "GET", url = "https://example.com"))
            }
        }
        started.await()

        bridge.close()

        val failure = withTimeout(5_000) { execution.await() }.exceptionOrNull()
        assertTrue("Expected cancellation, got ${failure?.javaClass?.simpleName}", failure is CancellationException)
    }

    private fun bridge(
        broker: RecordingBroker,
        granted: Set<Capability>,
    ): ExtensionHostBridge = ExtensionHostBridge(
        context = context,
        broker = broker,
        manifest = MANIFEST,
        envelope = envelope(granted),
    )

    private fun envelope(granted: Set<Capability>) = SerializedExtensionEnvelope(
        apiVersion = ExtensionApiVersion(1, 0),
        invocationId = InvocationId("invocation-1"),
        extensionId = EXTENSION_ID,
        hook = HOOK,
        schemaVersion = 1,
        payload = JsonObject(emptyMap()),
        grantedCapabilities = granted,
    )

    private fun execute(
        bridge: ExtensionHostBridge,
        request: BrokeredHttpRequest,
    ): BrokeredHttpResponse {
        val input = ParcelFileCodec.write(
            context,
            json.encodeToString(BrokerInvocation(accountId = "account-1", request = request)),
        )
        val output = bridge.executeHttp(input)
        return json.decodeFromString(ParcelFileCodec.read(output, 64 * 1024))
    }

    private inline fun <reified T : Throwable> expectThrows(block: () -> Unit) {
        try {
            block()
            fail("Expected ${T::class.java.simpleName}")
        } catch (error: Throwable) {
            assertTrue(
                "Expected ${T::class.java.simpleName}, got ${error::class.java.simpleName}",
                error is T,
            )
        }
    }

    private class RecordingBroker : HostNetworkBroker {
        var calls: Int = 0
        var extensionId: String? = null

        override suspend fun execute(
            extensionId: String,
            accountId: String,
            request: BrokeredHttpRequest,
        ): BrokeredHttpResponse {
            calls++
            this.extensionId = extensionId
            return BrokeredHttpResponse(
                statusCode = 200,
                headers = emptyMap(),
                body = "ok",
            )
        }
    }

    private companion object {
        val EXTENSION_ID = ExtensionId("com.example.bridge")
        val HOOK = Hook("example.fetch")
        val MANIFEST = ExtensionManifest(
            id = EXTENSION_ID,
            displayName = "Bridge test",
            extensionVersion = ExtensionSemanticVersion(1, 0, 0),
            apiRange = ExtensionApiRange(
                minimum = ExtensionApiVersion(1, 0),
                maximum = ExtensionApiVersion(1, 0),
            ),
            hooks = setOf(ExtensionHookDeclaration(HOOK, schemaVersion = 1)),
            capabilities = setOf(
                ExtensionCapabilityRequest(ExtensionCapabilityIds.Network, "Fetch provider data"),
                ExtensionCapabilityRequest(ExtensionCapabilityIds.CredentialRead, "Authenticate requests"),
                ExtensionCapabilityRequest(ExtensionCapabilityIds.CredentialWrite, "Store login results"),
            ),
        )
    }
}
