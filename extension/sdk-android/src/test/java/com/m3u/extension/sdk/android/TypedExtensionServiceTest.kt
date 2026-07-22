package com.m3u.extension.sdk.android

import com.m3u.extension.api.Capability
import com.m3u.extension.api.ExtensionApiRange
import com.m3u.extension.api.ExtensionApiVersion
import com.m3u.extension.api.ExtensionApiVersions
import com.m3u.extension.api.ExtensionCallContext
import com.m3u.extension.api.ExtensionCapabilityIds
import com.m3u.extension.api.ExtensionCapabilityRequest
import com.m3u.extension.api.ExtensionErrorCodes
import com.m3u.extension.api.ExtensionHookDeclaration
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.ExtensionManifest
import com.m3u.extension.api.ExtensionSemanticVersion
import com.m3u.extension.api.ExtensionSettingField
import com.m3u.extension.api.ExtensionSettingSchema
import com.m3u.extension.api.ExtensionSettingSection
import com.m3u.extension.api.ExtensionSettingsSnapshot
import com.m3u.extension.api.ExtensionSettingType
import com.m3u.extension.api.HookResult
import com.m3u.extension.api.HostHookSpecs
import com.m3u.extension.api.InvocationId
import com.m3u.extension.api.SerializedExtensionEnvelope
import com.m3u.extension.api.SettingsSchemaRequest
import com.m3u.extension.api.SettingsSchemaResult
import com.m3u.extension.api.security.BrokeredHttpRequest
import com.m3u.extension.api.security.BrokeredHttpResponse
import com.m3u.extension.api.security.BrokerValue
import com.m3u.extension.runtime.ExtensionTransportHealth
import java.util.concurrent.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

class TypedExtensionServiceTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun `typed handler receives decoded request and host-scoped context`() = runBlocking {
        lateinit var receivedRequest: SettingsSchemaRequest
        lateinit var receivedContext: ExtensionCallContext
        val registry = TypedHookRegistry().apply {
            handle(HostHookSpecs.SettingsSchema) { request, context ->
                receivedRequest = request
                receivedContext = context
                HookResult.Success(SETTINGS_RESULT)
            }
        }
        val transport = registry.createTransport(manifest(), json)
        val settings = ExtensionSettingsSnapshot(
            schemaVersions = mapOf("manifest" to 1),
            values = mapOf("manifest/greeting" to JsonPrimitive("Hello")),
        )

        val result = transport.invoke(
            envelope(
                settings = settings,
                grantedCapabilities = setOf(
                    ExtensionCapabilityIds.SettingsContribute,
                    ExtensionCapabilityIds.SearchRead,
                ),
            )
        )

        assertEquals(SettingsSchemaRequest("en-US", "phone"), receivedRequest)
        assertEquals(settings, receivedContext.settings)
        assertEquals(
            setOf(ExtensionCapabilityIds.SettingsContribute),
            receivedContext.grantedCapabilities,
        )
        assertEquals(
            SETTINGS_RESULT,
            json.decodeFromJsonElement(
                HostHookSpecs.SettingsSchema.responseSerializer,
                requireNotNull(result.payload),
            ),
        )
        assertEquals(ExtensionTransportHealth.HEALTHY, transport.health())
    }

    @Test
    fun `broker-backed typed handler receives the invocation-scoped broker`() = runBlocking {
        lateinit var receivedBrokerRequest: BrokeredHttpRequest
        val registry = TypedHookRegistry().apply {
            handleWithBroker(HostHookSpecs.SettingsSchema) { _, _, broker ->
                val response = broker.execute(
                    BrokeredHttpRequest(
                        method = "GET",
                        url = "https://media.example.test/system/info",
                    )
                )
                assertEquals(204, response.statusCode)
                HookResult.Success(SETTINGS_RESULT)
            }
        }
        val transport = registry.createTransport(manifest(), json)
        val broker = ExtensionHostNetworkBroker.forHttpTesting { request ->
            receivedBrokerRequest = request
            BrokeredHttpResponse(
                statusCode = 204,
                headers = emptyMap(),
                body = "",
            )
        }

        val result = transport.invoke(envelope(), broker)

        assertEquals("GET", receivedBrokerRequest.method)
        assertEquals(
            BrokerValue.Literal(
                "https://media.example.test/system/info"
            ),
            receivedBrokerRequest.url,
        )
        assertEquals(
            SETTINGS_RESULT,
            json.decodeFromJsonElement(
                HostHookSpecs.SettingsSchema.responseSerializer,
                requireNotNull(result.payload),
            ),
        )
    }

    @Test
    fun `broker-backed typed handler fails safely when invoked without a host broker`() = runBlocking {
        var invoked = false
        val registry = TypedHookRegistry().apply {
            handleWithBroker(HostHookSpecs.SettingsSchema) { _, _, _ ->
                invoked = true
                HookResult.Success(SETTINGS_RESULT)
            }
        }
        val transport = registry.createTransport(manifest(), json)

        val result = transport.invoke(envelope())

        assertFalse(invoked)
        assertEquals(ExtensionErrorCodes.InvocationFailed, result.error?.code)
        assertEquals("Host network broker is unavailable", result.error?.message)
    }

    @Test
    fun `missing required host grant returns an error without invoking handler`() = runBlocking {
        var invoked = false
        val registry = TypedHookRegistry().apply {
            handle(HostHookSpecs.SettingsSchema) { _, _ ->
                invoked = true
                HookResult.Success(SETTINGS_RESULT)
            }
        }
        val transport = registry.createTransport(manifest(), json)

        val result = transport.invoke(envelope(grantedCapabilities = emptySet()))

        assertFalse(invoked)
        assertEquals(ExtensionErrorCodes.CapabilityDenied, result.error?.code)
    }

    @Test
    fun `invalid request and handler exception become stable error envelopes`() = runBlocking {
        val registry = TypedHookRegistry().apply {
            handle(HostHookSpecs.SettingsSchema) { _, _ ->
                error("secret=must-not-escape")
            }
        }
        val transport = registry.createTransport(manifest(), json)

        val invalidPayload = transport.invoke(
            envelope(payload = JsonObject(emptyMap()))
        )
        val failedHandler = transport.invoke(
            envelope(invocationId = InvocationId("call-2"))
        )

        assertEquals(ExtensionErrorCodes.SchemaIncompatible, invalidPayload.error?.code)
        assertEquals(ExtensionErrorCodes.InvocationFailed, failedHandler.error?.code)
        assertFalse(failedHandler.error?.message.orEmpty().contains("secret"))
        assertEquals("IllegalStateException", failedHandler.error?.details?.get("exception"))
    }

    @Test
    fun `cancel stops the active typed handler`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val registry = TypedHookRegistry().apply {
            handle(HostHookSpecs.SettingsSchema) { _, _ ->
                started.complete(Unit)
                awaitCancellation()
            }
        }
        val transport = registry.createTransport(manifest(), json)
        val invocation = async { transport.invoke(envelope()) }
        started.await()

        transport.cancel(InvocationId("call-1"))

        assertFailsWith<CancellationException> { invocation.await() }
        assertTrue(invocation.isCancelled)
    }

    @Test
    fun `cancel before registration prevents the typed handler from starting`() = runBlocking {
        var invoked = false
        val registry = TypedHookRegistry().apply {
            handle(HostHookSpecs.SettingsSchema) { _, _ ->
                invoked = true
                HookResult.Success(SETTINGS_RESULT)
            }
        }
        val transport = registry.createTransport(manifest(), json)

        transport.cancel(InvocationId("call-1"))
        val invocation = async { transport.invoke(envelope()) }

        assertFailsWith<CancellationException> { invocation.await() }
        assertFalse(invoked)
    }

    @Test
    fun `typed transport negotiates compatible API minor versions through hook schema`() = runBlocking {
        val registry = TypedHookRegistry().apply {
            handle(HostHookSpecs.SettingsSchema) { _, _ -> HookResult.Success(SETTINGS_RESULT) }
        }
        val transport = registry.createTransport(
            manifest(
                apiRange = ExtensionApiRange(
                    minimum = ExtensionApiVersion(major = 1, minor = 0),
                    maximum = ExtensionApiVersion(major = 1, minor = 0),
                )
            ),
            json,
        )

        val result = transport.invoke(
            envelope(apiVersion = ExtensionApiVersion(major = 1, minor = 9))
        )

        assertEquals(
            SETTINGS_RESULT,
            json.decodeFromJsonElement(
                HostHookSpecs.SettingsSchema.responseSerializer,
                requireNotNull(result.payload),
            ),
        )
    }

    private fun envelope(
        apiVersion: ExtensionApiVersion = ExtensionApiVersions.Current,
        invocationId: InvocationId = InvocationId("call-1"),
        payload: JsonElement = json.encodeToJsonElement(
            HostHookSpecs.SettingsSchema.requestSerializer,
            SettingsSchemaRequest(localeTag = "en-US", surface = "phone"),
        ),
        settings: ExtensionSettingsSnapshot = ExtensionSettingsSnapshot(),
        grantedCapabilities: Set<Capability> = setOf(
            ExtensionCapabilityIds.SettingsContribute
        ),
    ) = SerializedExtensionEnvelope(
        apiVersion = apiVersion,
        invocationId = invocationId,
        extensionId = EXTENSION_ID,
        hook = HostHookSpecs.SettingsSchema.hook,
        schemaVersion = HostHookSpecs.SettingsSchema.schemaVersion,
        payload = payload,
        settings = settings,
        grantedCapabilities = grantedCapabilities,
    )

    private fun manifest(
        apiRange: ExtensionApiRange = ExtensionApiRange(
            minimum = ExtensionApiVersions.Current,
            maximum = ExtensionApiVersions.Current,
        ),
    ) = ExtensionManifest(
        id = EXTENSION_ID,
        displayName = "Typed SDK test",
        extensionVersion = ExtensionSemanticVersion(1, 0, 0),
        apiRange = apiRange,
        hooks = setOf(
            ExtensionHookDeclaration(
                hook = HostHookSpecs.SettingsSchema.hook,
                schemaVersion = HostHookSpecs.SettingsSchema.schemaVersion,
                requiredCapabilities = setOf(ExtensionCapabilityIds.SettingsContribute),
            )
        ),
        capabilities = setOf(
            ExtensionCapabilityRequest(
                capability = ExtensionCapabilityIds.SettingsContribute,
                reason = "Contribute settings",
            )
        ),
    )

    private companion object {
        val EXTENSION_ID = ExtensionId("com.example.typed")
        val SETTINGS_RESULT = SettingsSchemaResult(
            sections = listOf(
                ExtensionSettingSection(
                    id = "device",
                    title = "Device",
                    schema = ExtensionSettingSchema(
                        version = 1,
                        fields = listOf(
                            ExtensionSettingField(
                                key = "name",
                                label = "Name",
                                type = ExtensionSettingType.TEXT,
                            )
                        ),
                    ),
                )
            ),
        )
    }
}
