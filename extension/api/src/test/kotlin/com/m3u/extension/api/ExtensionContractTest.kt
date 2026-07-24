package com.m3u.extension.api

import com.m3u.extension.api.security.BrokerAuthenticationRequest
import com.m3u.extension.api.security.BrokerAuthenticationResponse
import com.m3u.extension.api.security.BrokerErrorCodes
import com.m3u.extension.api.security.BrokerHttpExchange
import com.m3u.extension.api.security.BrokerInvocation
import com.m3u.extension.api.security.BrokerInvocationError
import com.m3u.extension.api.security.BrokerInvocationResult
import com.m3u.extension.api.security.BrokerOperation
import com.m3u.extension.api.security.BrokerOperationResult
import com.m3u.extension.api.security.BrokerProtocolVersions
import com.m3u.extension.api.security.BrokerResponseRedaction
import com.m3u.extension.api.security.BrokerScopeHandle
import com.m3u.extension.api.security.BrokerValue
import com.m3u.extension.api.security.BrokerValueEncoding
import com.m3u.extension.api.security.BrokeredHttpRequest
import com.m3u.extension.api.security.BrokeredHttpResponse
import com.m3u.extension.api.security.CredentialHandle
import com.m3u.extension.api.security.HostNetworkBrokerHooks
import com.m3u.extension.api.security.ContextReference
import com.m3u.extension.api.security.OpaqueContextCapture
import com.m3u.extension.api.security.ProviderAuthenticationReceipt
import com.m3u.extension.api.security.ResponseValueSource
import com.m3u.extension.api.subscription.PlaybackSessionDescriptor
import com.m3u.extension.api.security.SecretReference
import com.m3u.extension.api.security.referencesCredential
import com.m3u.extension.api.security.referencesOpaqueContext
import com.m3u.extension.api.subscription.PlaybackHeaderValue
import com.m3u.extension.api.subscription.PlaybackSourceResolveResult
import com.m3u.extension.api.subscription.ProviderKind
import com.m3u.extension.api.subscription.SubscriptionProviderDescriptor
import com.m3u.extension.api.subscription.SubscriptionProviderVariant
import com.m3u.extension.api.subscription.SubscriptionHookSpecs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class ExtensionContractTest {
    @Test
    fun `number settings reject non-finite defaults`() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach { value ->
            assertFailsWith<IllegalArgumentException> {
                ExtensionSettingField(
                    key = "ratio",
                    label = "Ratio",
                    type = ExtensionSettingType.NUMBER,
                    defaultValue = JsonPrimitive(value),
                )
            }
        }
        assertEquals(
            JsonPrimitive(1.5),
            ExtensionSettingField(
                key = "ratio",
                label = "Ratio",
                type = ExtensionSettingType.NUMBER,
                defaultValue = JsonPrimitive(1.5),
            ).defaultValue,
        )
    }

    @Test
    fun `playback session requires bounded non-blank identifiers`() {
        assertEquals(
            "session-1",
            PlaybackSessionDescriptor(playSessionId = "session-1").playSessionId,
        )
        assertFailsWith<IllegalArgumentException> {
            PlaybackSessionDescriptor()
        }
        assertFailsWith<IllegalArgumentException> {
            PlaybackSessionDescriptor(playSessionId = " ")
        }
        assertFailsWith<IllegalArgumentException> {
            PlaybackSessionDescriptor(
                liveStreamId = "界".repeat(
                    PlaybackSessionDescriptor.MAX_IDENTIFIER_UTF8_BYTES / 3 + 1
                )
            )
        }
    }

    @Test
    fun `provider kind is a bounded wire identifier`() {
        assertEquals("reference", ProviderKind("reference").value)
        assertFailsWith<IllegalArgumentException> {
            ProviderKind("a".repeat(ProviderKind.MAX_LENGTH + 1))
        }
    }

    @Test
    fun `manifest accepts declared hook capabilities`() {
        val manifest = ExtensionManifest(
            id = ExtensionId("com.example.provider"),
            displayName = "Example",
            extensionVersion = ExtensionSemanticVersion(1, 0, 0),
            apiRange = ExtensionApiRange(
                minimum = ExtensionApiVersions.Current,
                maximum = ExtensionApiVersions.Current,
            ),
            hooks = setOf(
                ExtensionHookDeclaration(
                    hook = ExtensionHookIds.PlaybackSourceResolve,
                    requiredCapabilities = setOf(ExtensionCapabilityIds.PlaybackResolve),
                )
            ),
            capabilities = setOf(
                ExtensionCapabilityRequest(
                    capability = ExtensionCapabilityIds.PlaybackResolve,
                    reason = "Resolve a stable playback reference",
                )
            ),
        )

        assertEquals(ExtensionId("com.example.provider"), manifest.id)
        assertTrue(ExtensionApiVersions.Current in manifest.apiRange)
    }

    @Test
    fun `manifest rejects hook capability that was not requested`() {
        assertFailsWith<IllegalArgumentException> {
            ExtensionManifest(
                id = ExtensionId("com.example.provider"),
                displayName = "Example",
                extensionVersion = ExtensionSemanticVersion(1, 0, 0),
                apiRange = ExtensionApiRange(
                    minimum = ExtensionApiVersions.Current,
                    maximum = ExtensionApiVersions.Current,
                ),
                hooks = setOf(
                    ExtensionHookDeclaration(
                        hook = ExtensionHookIds.PlaybackSourceResolve,
                        requiredCapabilities = setOf(ExtensionCapabilityIds.PlaybackResolve),
                    )
                ),
                capabilities = emptySet(),
            )
        }
    }

    @Test
    fun `every privileged hook declares its base capability`() {
        ExtensionContractCatalog.RequiredCapabilitiesByHook.forEach { (hook, required) ->
            assertFailsWith<IllegalArgumentException>(hook.id) {
                ExtensionManifest(
                    id = ExtensionId("com.example.${hook.id.replace('.', '-')}"),
                    displayName = "Capability example",
                    extensionVersion = ExtensionSemanticVersion(1, 0, 0),
                    apiRange = ExtensionApiRange(
                        minimum = ExtensionApiVersions.Current,
                        maximum = ExtensionApiVersions.Current,
                    ),
                    hooks = setOf(
                        ExtensionHookDeclaration(
                            hook = hook,
                            requiredCapabilities = emptySet(),
                        )
                    ),
                    capabilities = required.mapTo(mutableSetOf()) { capability ->
                        ExtensionCapabilityRequest(
                            capability = capability,
                            reason = "Use ${capability.id}",
                        )
                    },
                )
            }
        }
    }

    @Test
    fun `background task declarations enforce stable ids intervals and count`() {
        assertEquals(
            6,
            ExtensionBackgroundTaskDeclaration(
                taskId = "catalog.refresh",
                repeatIntervalHours = 6,
            ).repeatIntervalHours,
        )
        listOf(
            "Catalog Refresh",
            "catalog/refresh",
            "catalog..refresh",
            "a".repeat(129),
        ).forEach { taskId ->
            assertFailsWith<IllegalArgumentException> {
                ExtensionBackgroundTaskDeclaration(taskId, repeatIntervalHours = 24)
            }
        }
        listOf(5, 169).forEach { repeatIntervalHours ->
            assertFailsWith<IllegalArgumentException> {
                ExtensionBackgroundTaskDeclaration("catalog.refresh", repeatIntervalHours)
            }
        }
        assertEquals(
            ExtensionManifest.MAX_BACKGROUND_TASKS,
            backgroundTaskManifest(
                backgroundTasks = List(ExtensionManifest.MAX_BACKGROUND_TASKS) { index ->
                    ExtensionBackgroundTaskDeclaration("task-$index", repeatIntervalHours = 168)
                }
            ).backgroundTasks.size,
        )
        assertFailsWith<IllegalArgumentException> {
            backgroundTaskManifest(
                backgroundTasks = List(ExtensionManifest.MAX_BACKGROUND_TASKS + 1) { index ->
                    ExtensionBackgroundTaskDeclaration("task-$index", repeatIntervalHours = 24)
                }
            )
        }
        assertFailsWith<IllegalArgumentException> {
            backgroundTaskManifest(
                backgroundTasks = listOf(
                    ExtensionBackgroundTaskDeclaration("catalog.refresh", 24),
                    ExtensionBackgroundTaskDeclaration("catalog.refresh", 48),
                )
            )
        }
    }

    @Test
    fun `background task declarations require the typed hook and capability`() {
        val task = ExtensionBackgroundTaskDeclaration("catalog.refresh", 24)
        assertFailsWith<IllegalArgumentException> {
            backgroundTaskManifest(
                backgroundTasks = listOf(task),
                hooks = emptySet(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            backgroundTaskManifest(
                backgroundTasks = listOf(task),
                hooks = setOf(
                    ExtensionHookDeclaration(
                        hook = HostHookSpecs.BackgroundTask.hook,
                        schemaVersion = HostHookSpecs.BackgroundTask.schemaVersion,
                    )
                ),
            )
        }
        assertEquals(
            listOf(task),
            backgroundTaskManifest(backgroundTasks = listOf(task)).backgroundTasks,
        )
        assertTrue(backgroundTaskManifest().backgroundTasks.isEmpty())
    }

    @Test
    fun `background task result schema uses hook failures for retry semantics`() {
        val json = Json
        val result = BackgroundTaskResult(output = mapOf("synced" to "true"))

        assertEquals("""{"output":{"synced":"true"}}""", json.encodeToString(result))
        assertEquals(2, HostHookSpecs.BackgroundTask.schemaVersion)
        assertEquals(
            setOf(HostHookSpecs.BackgroundTask.schemaVersion),
            ExtensionContractCatalog.SupportedHookSchemaVersions
                .getValue(HostHookSpecs.BackgroundTask.hook),
        )
    }

    @Test
    fun `contract identifiers reject ambiguous values`() {
        assertFailsWith<IllegalArgumentException> { ExtensionId("Example Provider") }
        assertFailsWith<IllegalArgumentException> { ExtensionId("a".repeat(129)) }
        assertFailsWith<IllegalArgumentException> { Hook("playback/source") }
        assertFailsWith<IllegalArgumentException> { Capability("") }
    }

    @Test
    fun `semantic version follows prerelease precedence`() {
        val ordered = listOf(
            "alpha",
            "alpha.1",
            "alpha.beta",
            "beta",
            "beta.2",
            "beta.11",
            "rc.1",
            null,
        ).map { preRelease ->
            ExtensionSemanticVersion(1, 0, 0, preRelease)
        }
        assertEquals(ordered, ordered.shuffled().sorted())
        assertFailsWith<IllegalArgumentException> {
            ExtensionSemanticVersion(1, 0, 0, "alpha..1")
        }
        assertFailsWith<IllegalArgumentException> {
            ExtensionSemanticVersion(1, 0, 0, "01")
        }
    }

    @Test
    fun `serialized envelope keeps old fixtures compatible and carries host grants`() {
        val json = Json { ignoreUnknownKeys = true }
        val oldFixture = """{"apiVersion":{"major":1,"minor":0},"invocationId":"call-1","extensionId":"com.example.provider","hook":"settings.schema.contribute","schemaVersion":1,"payload":{}}"""

        val decoded = json.decodeFromString<SerializedExtensionEnvelope>(oldFixture)

        assertTrue(decoded.grantedCapabilities.isEmpty())
        assertEquals(null, decoded.brokerScope)
        assertEquals(
            """{"apiVersion":{"major":1,"minor":0},"invocationId":"call-1","extensionId":"com.example.provider","hook":"settings.schema.contribute","schemaVersion":1,"payload":{},"grantedCapabilities":["settings.contribute"],"brokerScope":"broker-scope-1"}""",
            json.encodeToString(
                decoded.copy(
                    payload = JsonObject(emptyMap()),
                    grantedCapabilities = setOf(ExtensionCapabilityIds.SettingsContribute),
                    brokerScope = BrokerScopeHandle("broker-scope-1"),
                )
            ),
        )
    }

    @Test
    fun `broker v4 invocation has a required golden protocol version and no account id`() {
        val json = Json { ignoreUnknownKeys = true }
        val request = BrokeredHttpRequest(
            method = "GET",
            url = "https://media.example.test/system/info",
        )
        val invocation = BrokerInvocation(
            brokerProtocolVersion = BrokerProtocolVersions.Current,
            operation = BrokerOperation.Http(request),
        )

        assertEquals(
            """{"brokerProtocolVersion":4,"operation":{"type":"http","request":{"method":"GET","url":{"type":"literal","value":"https://media.example.test/system/info"}}}}""",
            json.encodeToString(invocation),
        )
        assertEquals(invocation, json.decodeFromString(json.encodeToString(invocation)))
        assertFails {
            json.decodeFromString<BrokerInvocation>(
                """{"accountId":"legacy-account","request":{"method":"GET","url":"https://media.example.test/system/info"}}"""
            )
        }
        assertFailsWith<IllegalArgumentException> {
            BrokerInvocation(
                brokerProtocolVersion = 2,
                operation = BrokerOperation.Http(request),
            )
        }
        assertFailsWith<IllegalArgumentException> { BrokerScopeHandle(" ") }
        assertEquals(
            BrokerProtocolVersions.Current,
            BrokerProtocolVersions.negotiate(setOf(2, BrokerProtocolVersions.Current)),
        )
        assertNull(BrokerProtocolVersions.negotiate(setOf(1, 2)))
    }

    @Test
    fun `broker v4 result has stable success and failure fixtures`() {
        val json = Json { ignoreUnknownKeys = true }
        val success: BrokerInvocationResult = BrokerInvocationResult.Success(
            BrokerOperationResult.Http(
                BrokeredHttpResponse(
                    statusCode = 204,
                    headers = mapOf("ETag" to "revision-1"),
                    body = "",
                )
            )
        )
        val failure: BrokerInvocationResult = BrokerInvocationResult.Failure(
            BrokerInvocationError(
                code = BrokerErrorCodes.Timeout,
                recoverable = true,
                message = "The broker request timed out",
            )
        )

        assertEquals(
            """{"type":"success","result":{"type":"http","response":{"statusCode":204,"headers":{"ETag":"revision-1"},"body":""}}}""",
            json.encodeToString(success),
        )
        assertEquals(
            """{"type":"failure","error":{"code":"timeout","recoverable":true,"message":"The broker request timed out"}}""",
            json.encodeToString(failure),
        )
        assertEquals(success, json.decodeFromString(json.encodeToString(success)))
        assertEquals(failure, json.decodeFromString(json.encodeToString(failure)))
        assertFailsWith<IllegalArgumentException> {
            BrokerInvocationError(
                code = BrokerErrorCodes.Internal,
                recoverable = false,
                message = "x".repeat(257),
            )
        }
    }

    @Test
    fun `broker v4 authentication is typed and returns no response plaintext`() {
        val json = Json { ignoreUnknownKeys = true }
        val contextUrl = BrokerValue.Concatenated(
            listOf(
                BrokerValue.Literal("https://media.example.test/users/"),
                BrokerValue.Encoded(
                    BrokerValue.Context(ContextReference("user_id")),
                    BrokerValueEncoding.FormUrlComponent,
                ),
            )
        )
        val request = BrokerAuthenticationRequest(
            exchange = BrokerHttpExchange(
                method = "POST",
                url = BrokerValue.Literal("https://media.example.test/login"),
            ),
            primaryCredentialSource = ResponseValueSource.JsonPointer("/accessToken"),
            opaqueContexts = listOf(
                OpaqueContextCapture(
                    key = "user_id",
                    source = ResponseValueSource.JsonPointer("/user/id"),
                )
            ),
        )
        val invocation = BrokerInvocation(
            brokerProtocolVersion = BrokerProtocolVersions.Current,
            operation = BrokerOperation.Authenticate(request),
        )
        val result: BrokerInvocationResult = BrokerInvocationResult.Success(
            BrokerOperationResult.Authentication(
                BrokerAuthenticationResponse(
                    statusCode = 200,
                    receipt = ProviderAuthenticationReceipt("receipt-1"),
                )
            )
        )

        assertEquals(
            """{"brokerProtocolVersion":4,"operation":{"type":"authenticate","request":{"exchange":{"method":"POST","url":{"type":"literal","value":"https://media.example.test/login"}},"primaryCredentialSource":{"type":"json_pointer","pointer":"/accessToken"},"opaqueContexts":[{"key":"user_id","source":{"type":"json_pointer","pointer":"/user/id"}}]}}}""",
            json.encodeToString(invocation),
        )
        assertEquals(
            """{"type":"success","result":{"type":"authentication","response":{"statusCode":200,"receipt":"receipt-1"}}}""",
            json.encodeToString(result),
        )
        assertEquals(result, json.decodeFromString(json.encodeToString(result)))
        assertTrue(contextUrl.referencesOpaqueContext())
        assertTrue(!contextUrl.referencesCredential())
        assertFailsWith<IllegalArgumentException> {
            request.copy(
                opaqueContexts = listOf(
                    OpaqueContextCapture(
                        "nested_token",
                        ResponseValueSource.JsonPointer("/accessToken/value"),
                    )
                )
            )
        }
        assertFailsWith<IllegalArgumentException> {
            BrokerAuthenticationResponse(statusCode = 200, receipt = null)
        }
        assertEquals("", ResponseValueSource.JsonPointer("").pointer)
        assertEquals(
            ResponseValueSource.JsonPointer("/accounts/0/access~1token/~0primary"),
            ResponseValueSource.JsonPointer("/accounts/0/access~1token/~0primary"),
        )
        listOf("/invalid~", "/invalid~2escape").forEach { pointer ->
            assertFailsWith<IllegalArgumentException> {
                ResponseValueSource.JsonPointer(pointer)
            }
        }
    }

    @Test
    fun `broker request and opaque playback headers have stable typed fixtures`() {
        val json = Json { ignoreUnknownKeys = true }
        val request = BrokeredHttpRequest(
            method = "POST",
            url = "https://media.example.test/login",
        )
        assertEquals(
            """{"method":"POST","url":{"type":"literal","value":"https://media.example.test/login"}}""",
            json.encodeToString(request),
        )

        val composedHeader: BrokerValue = BrokerValue.Concatenated(
            listOf(
                BrokerValue.Literal("Basic "),
                BrokerValue.Encoded(
                    value = BrokerValue.Secret(
                        SecretReference(CredentialHandle("provider-token"))
                    ),
                    encoding = BrokerValueEncoding.Base64,
                ),
            )
        )
        assertEquals(
            """{"type":"concatenated","parts":[{"type":"literal","value":"Basic "},{"type":"encoded","value":{"type":"secret","reference":{"handle":"provider-token"}},"encoding":"base64"}]}""",
            json.encodeToString(composedHeader),
        )
        assertTrue(
            BrokeredHttpRequest(
                method = "GET",
                url = BrokerValue.Concatenated(
                    listOf(
                        BrokerValue.Literal("https://media.example.test/users/"),
                        BrokerValue.Encoded(
                            BrokerValue.Context(ContextReference("user_id")),
                            BrokerValueEncoding.FormUrlComponent,
                        ),
                    )
                ),
                headers = mapOf(
                    "Authorization" to composedHeader,
                    "X-Provider-User" to BrokerValue.Context(ContextReference("user_id")),
                ),
            ).headers.getValue("Authorization").referencesCredential()
        )
        assertFailsWith<IllegalArgumentException> {
            BrokeredHttpRequest(
                method = "GET",
                url = BrokerValue.Concatenated(
                    listOf(
                        BrokerValue.Literal("https://media.example.test/token/"),
                        BrokerValue.Secret(SecretReference(CredentialHandle("provider-token"))),
                    )
                ),
            )
        }
        assertTrue(
            BrokerHttpExchange(
                method = "POST",
                url = "https://media.example.test/login",
                body = listOf(composedHeader),
            ).body.single().referencesCredential()
        )
        assertTrue(
            HostNetworkBrokerHooks.supports(ExtensionHookIds.SubscriptionProviderValidate)
        )
        assertTrue(
            HostNetworkBrokerHooks.supports(ExtensionHookIds.SearchProviderQuery)
        )
        assertEquals(composedHeader, json.decodeFromString(json.encodeToString(composedHeader)))

        val result = PlaybackSourceResolveResult(
            url = "https://media.example.test/stream/1",
            headers = mapOf(
                "Authorization" to PlaybackHeaderValue(
                    parts = listOf(
                        BrokerValue.Literal("Bearer "),
                        BrokerValue.Secret(
                            SecretReference(CredentialHandle("provider-token"))
                        ),
                    )
                )
            ),
        )
        val encoded = json.encodeToString(result)

        assertEquals(result, json.decodeFromString(encoded))
        assertTrue(encoded.contains("provider-token"))
        assertEquals(4, SubscriptionHookSpecs.ResolvePlayback.schemaVersion)
        assertEquals(
            setOf(SubscriptionHookSpecs.ResolvePlayback.schemaVersion),
            ExtensionContractCatalog.SupportedHookSchemaVersions
                .getValue(SubscriptionHookSpecs.ResolvePlayback.hook),
        )
    }

    @Test
    fun `broker response redaction recognizes only the closed authentication field set`() {
        listOf(
            "token",
            "accessToken",
            "access_token",
            "refresh-token",
            "CLIENT SECRET",
            "apiKey",
        ).forEach { name ->
            assertTrue(BrokerResponseRedaction.isAuthenticationField(name), name)
        }
        listOf(
            "nextPageToken",
            "continuationToken",
            "tokenType",
            "tokenExpiry",
            "credentialType",
            "secretary",
        ).forEach { name ->
            assertFalse(BrokerResponseRedaction.isAuthenticationField(name), name)
        }
    }

    @Test
    fun `provider discovery preserves declared variant order`() {
        val json = Json { ignoreUnknownKeys = true }
        val descriptor = SubscriptionProviderDescriptor(
            providerId = ExtensionId("com.example.provider"),
            displayName = "Example",
            variants = listOf(
                SubscriptionProviderVariant(ProviderKind("jellyfin"), "Jellyfin"),
                SubscriptionProviderVariant(ProviderKind("emby"), "Emby"),
            ),
        )

        assertEquals(
            """{"providerId":"com.example.provider","displayName":"Example","variants":[{"kind":"jellyfin","displayName":"Jellyfin"},{"kind":"emby","displayName":"Emby"}]}""",
            json.encodeToString(descriptor),
        )
        assertEquals(
            listOf("jellyfin", "emby"),
            json.decodeFromString<SubscriptionProviderDescriptor>(json.encodeToString(descriptor))
                .variants
                .map { variant -> variant.kind.value },
        )
        assertEquals(3, SubscriptionHookSpecs.Discover.schemaVersion)
        assertEquals(4, SubscriptionHookSpecs.Refresh.schemaVersion)
        assertEquals(
            setOf(3),
            ExtensionContractCatalog.SupportedHookSchemaVersions
                .getValue(SubscriptionHookSpecs.Discover.hook),
        )
        assertFailsWith<IllegalArgumentException> {
            descriptor.copy(
                variants = listOf(
                    SubscriptionProviderVariant(ProviderKind("emby"), "Emby"),
                    SubscriptionProviderVariant(ProviderKind("emby"), "Duplicate"),
                )
            )
        }
    }

    private fun backgroundTaskManifest(
        backgroundTasks: List<ExtensionBackgroundTaskDeclaration> = emptyList(),
        hooks: Set<ExtensionHookDeclaration> = setOf(
            ExtensionHookDeclaration(
                hook = HostHookSpecs.BackgroundTask.hook,
                schemaVersion = HostHookSpecs.BackgroundTask.schemaVersion,
                requiredCapabilities = setOf(ExtensionCapabilityIds.BackgroundTask),
            )
        ),
        capabilities: Set<ExtensionCapabilityRequest> = setOf(
            ExtensionCapabilityRequest(
                capability = ExtensionCapabilityIds.BackgroundTask,
                reason = "Refresh extension-managed data",
            )
        ),
    ) = ExtensionManifest(
        id = ExtensionId("com.example.background"),
        displayName = "Background example",
        extensionVersion = ExtensionSemanticVersion(1, 0, 0),
        apiRange = ExtensionApiRange(
            minimum = ExtensionApiVersions.Current,
            maximum = ExtensionApiVersions.Current,
        ),
        hooks = hooks,
        capabilities = capabilities,
        backgroundTasks = backgroundTasks,
    )
}
