package com.m3u.data.extension.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.m3u.data.database.model.ProviderCredentialEntity
import com.m3u.extension.api.Capability
import com.m3u.extension.api.ExtensionApiRange
import com.m3u.extension.api.ExtensionApiVersion
import com.m3u.extension.api.ExtensionCapabilityIds
import com.m3u.extension.api.ExtensionCapabilityRequest
import com.m3u.extension.api.ExtensionContractCatalog
import com.m3u.extension.api.ExtensionHookDeclaration
import com.m3u.extension.api.ExtensionHookIds
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.ExtensionManifest
import com.m3u.extension.api.ExtensionSemanticVersion
import com.m3u.extension.api.Hook
import com.m3u.extension.api.InvocationId
import com.m3u.extension.api.SerializedExtensionEnvelope
import com.m3u.extension.api.security.BrokerAuthenticationRequest
import com.m3u.extension.api.security.BrokerErrorCodes
import com.m3u.extension.api.security.BrokerHttpExchange
import com.m3u.extension.api.security.BrokerInvocation
import com.m3u.extension.api.security.BrokerInvocationError
import com.m3u.extension.api.security.BrokerInvocationResult
import com.m3u.extension.api.security.BrokerOperation
import com.m3u.extension.api.security.BrokerOperationResult
import com.m3u.extension.api.security.BrokerProtocolVersions
import com.m3u.extension.api.security.BrokerScopeHandle
import com.m3u.extension.api.security.BrokerValue
import com.m3u.extension.api.security.BrokeredHttpRequest
import com.m3u.extension.api.security.BrokeredHttpResponse
import com.m3u.extension.api.security.CredentialHandle
import com.m3u.extension.api.security.ResponseValueSource
import com.m3u.extension.api.security.SecretReference
import com.m3u.extension.transport.android.ParcelFileCodec
import com.m3u.extension.transport.android.ExtensionResultDispatcher
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExtensionHostBridgeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val json = Json { explicitNulls = false }

    @Test
    fun networkCapabilityIsCheckedByHostBeforeBrokerExecution() {
        val fixture = scopeFixture()
        val broker = RecordingBroker(fixture.store)
        val bridge = bridge(
            broker = broker,
            granted = emptySet(),
            scope = fixture.scope,
        )

        val error = executeFailure(
            bridge,
            BrokeredHttpRequest(method = "GET", url = "$BASE_URL/items"),
        )

        assertEquals(BrokerErrorCodes.CapabilityDenied, error.code)
        assertEquals(0, broker.calls)
    }

    @Test
    fun credentialReadAndWriteUseSeparateHostCapabilities() {
        val fixture = scopeFixture()
        val broker = RecordingBroker(fixture.store)
        val writeOnly = bridge(
            broker = broker,
            granted = setOf(
                ExtensionCapabilityIds.Network,
                ExtensionCapabilityIds.CredentialWrite,
            ),
            scope = fixture.scope,
        )
        val credentialLoginRequest = BrokerAuthenticationRequest(
            exchange = BrokerHttpExchange(
                method = "POST",
                url = "$BASE_URL/login",
                body = listOf(
                    BrokerValue.Secret(
                        SecretReference(CredentialHandle("extension-secret:test"))
                    )
                ),
            ),
            primaryCredentialSource = ResponseValueSource.JsonPointer("/token"),
        )

        assertEquals(
            BrokerErrorCodes.CapabilityDenied,
            executeAuthenticationFailure(writeOnly, credentialLoginRequest).code,
        )

        val readOnly = bridge(
            broker = broker,
            granted = setOf(
                ExtensionCapabilityIds.Network,
                ExtensionCapabilityIds.CredentialRead,
            ),
            scope = fixture.scope,
        )
        val captureRequest = BrokerAuthenticationRequest(
            exchange = BrokerHttpExchange(
                method = "POST",
                url = "$BASE_URL/login",
            ),
            primaryCredentialSource = ResponseValueSource.JsonPointer("/token"),
        )

        assertEquals(
            BrokerErrorCodes.CapabilityDenied,
            executeAuthenticationFailure(readOnly, captureRequest).code,
        )
        assertEquals(0, broker.calls)
    }

    @Test
    fun ordinaryHttpRequiresCredentialReadBeforeDelegatingCredentialReferences() {
        val fixture = scopeFixture()
        val broker = RecordingBroker(fixture.store)
        val request =
            """
            {
              "brokerProtocolVersion": 4,
              "operation": {
                "type": "http",
                "request": {
                  "method": "GET",
                  "url": {"type": "literal", "value": "$BASE_URL/items"},
                  "headers": {
                    "Authorization": {
                      "type": "secret",
                      "reference": {"handle": "extension-secret:test"}
                    }
                  }
                }
              }
            }
            """.trimIndent()
        val networkOnly = bridge(
            broker = broker,
            granted = setOf(ExtensionCapabilityIds.Network),
            scope = fixture.scope,
        )

        val denied = executeRaw(networkOnly, request) as BrokerInvocationResult.Failure

        assertEquals(BrokerErrorCodes.CapabilityDenied, denied.error.code)
        assertEquals(0, broker.calls)

        val credentialReader = bridge(
            broker = broker,
            granted = setOf(
                ExtensionCapabilityIds.Network,
                ExtensionCapabilityIds.CredentialRead,
            ),
            scope = fixture.scope,
        )

        val result = executeRaw(credentialReader, request)

        assertTrue(result is BrokerInvocationResult.Success)
        assertEquals(1, broker.calls)
    }

    @Test
    fun grantedInvocationDelegatesWithBoundPrincipalHookAndScope() {
        val fixture = scopeFixture()
        val broker = RecordingBroker(fixture.store)
        val bridge = bridge(
            broker = broker,
            granted = setOf(ExtensionCapabilityIds.Network),
            scope = fixture.scope,
        )

        val response = executeSuccess(
            bridge,
            BrokeredHttpRequest(method = "GET", url = "$BASE_URL/channels"),
        )

        assertEquals(200, response.statusCode)
        assertEquals(PRINCIPAL, broker.principal)
        assertEquals(HOOK, broker.hook)
        assertEquals(fixture.scope, broker.scope)
        assertEquals(1, broker.calls)
    }

    @Test
    fun invocationWithoutHostScopeIsRejectedBeforeBrokerExecution() {
        val fixture = scopeFixture()
        val broker = RecordingBroker(fixture.store)
        val bridge = bridge(
            broker = broker,
            granted = setOf(ExtensionCapabilityIds.Network),
            scope = null,
        )

        val error = executeFailure(
            bridge,
            BrokeredHttpRequest(method = "GET", url = "$BASE_URL/items"),
        )

        assertEquals(BrokerErrorCodes.ScopeDenied, error.code)
        assertEquals(0, broker.calls)
    }

    @Test
    fun hookWithoutExplicitNetworkRequirementCannotUseBrokerWithForgedScope() {
        val fixture = scopeFixture()
        val broker = RecordingBroker(fixture.store)
        val bridge = bridge(
            broker = broker,
            granted = setOf(ExtensionCapabilityIds.Network),
            scope = fixture.scope,
            hook = ExtensionHookIds.BackgroundTaskRun,
            hookRequiresNetwork = false,
        )

        val error = executeFailure(
            bridge,
            BrokeredHttpRequest(method = "GET", url = "$BASE_URL/items"),
        )

        assertEquals(BrokerErrorCodes.ScopeDenied, error.code)
        assertEquals(0, broker.calls)
    }

    @Test
    fun scopeCannotBeUsedByAnotherPrincipal() {
        val fixture = scopeFixture()
        val broker = RecordingBroker(fixture.store)
        val bridge = bridge(
            broker = broker,
            granted = setOf(ExtensionCapabilityIds.Network),
            scope = fixture.scope,
            principal = WRONG_PRINCIPAL,
        )

        val error = executeFailure(
            bridge,
            BrokeredHttpRequest(method = "GET", url = "$BASE_URL/items"),
        )

        assertEquals(BrokerErrorCodes.ScopeDenied, error.code)
        assertEquals(1, broker.calls)
    }

    @Test
    fun scopeCannotBeUsedByAnotherHook() {
        val fixture = scopeFixture()
        val broker = RecordingBroker(fixture.store)
        val bridge = bridge(
            broker = broker,
            granted = setOf(ExtensionCapabilityIds.Network),
            scope = fixture.scope,
            hook = ExtensionHookIds.SubscriptionContentRefresh,
        )

        val error = executeFailure(
            bridge,
            BrokeredHttpRequest(method = "GET", url = "$BASE_URL/items"),
        )

        assertEquals(BrokerErrorCodes.ScopeDenied, error.code)
        assertEquals(1, broker.calls)
    }

    @Test
    fun invocationBridgeCannotBeReusedAfterClose() {
        val fixture = scopeFixture()
        val broker = RecordingBroker(fixture.store)
        val bridge = bridge(
            broker = broker,
            granted = setOf(ExtensionCapabilityIds.Network),
            scope = fixture.scope,
        )
        bridge.close()

        val error = executeFailure(
            bridge,
            BrokeredHttpRequest(method = "GET", url = "$BASE_URL/items"),
        )

        assertEquals(BrokerErrorCodes.Cancelled, error.code)
        assertEquals(0, broker.calls)
    }

    @Test
    fun closingInvocationBridgeCancelsAnInFlightBrokerRequest() = runBlocking {
        val fixture = scopeFixture()
        val started = CompletableDeferred<Unit>()
        val broker = object : ProviderHostNetworkBroker {
            override suspend fun execute(
                scope: BrokerScopeHandle,
                principal: ExtensionPrincipal,
                hook: Hook,
                request: BrokeredHttpRequest,
            ): BrokeredHttpResponse {
                fixture.store.authorize(scope, principal, hook)
                started.complete(Unit)
                awaitCancellation()
            }
        }
        val bridge = bridge(
            broker = broker,
            granted = setOf(ExtensionCapabilityIds.Network),
            scope = fixture.scope,
        )
        val execution = async(Dispatchers.IO) {
            execute(bridge, BrokeredHttpRequest(method = "GET", url = "$BASE_URL/items"))
        }
        started.await()

        bridge.close()

        val result = withTimeout(5_000) { execution.await() }
        val error = (result as BrokerInvocationResult.Failure).error
        assertEquals(BrokerErrorCodes.Cancelled, error.code)
    }

    @Test
    fun cancellingOneBrokerRequestPropagatesToItsHostExecution() = runBlocking {
        val fixture = scopeFixture()
        val started = CompletableDeferred<Unit>()
        val requestId = CompletableDeferred<String>()
        val broker = object : ProviderHostNetworkBroker {
            override suspend fun execute(
                scope: BrokerScopeHandle,
                principal: ExtensionPrincipal,
                hook: Hook,
                request: BrokeredHttpRequest,
            ): BrokeredHttpResponse {
                fixture.store.authorize(scope, principal, hook)
                started.complete(Unit)
                awaitCancellation()
            }
        }
        val bridge = bridge(
            broker = broker,
            granted = setOf(ExtensionCapabilityIds.Network),
            scope = fixture.scope,
        )
        val execution = async(Dispatchers.IO) {
            ExtensionResultDispatcher().use { dispatcher ->
                val input = ParcelFileCodec.write(
                    context,
                    json.encodeToString(
                        BrokerInvocation(
                            brokerProtocolVersion = BrokerProtocolVersions.Current,
                            operation = BrokerOperation.Http(
                                BrokeredHttpRequest(
                                    method = "GET",
                                    url = "$BASE_URL/items",
                                )
                            ),
                        )
                    ),
                )
                input.use { request ->
                    dispatcher.await { id, callback ->
                        requestId.complete(id)
                        bridge.executeHttp(id, request, callback)
                    }
                }.use { output ->
                    json.decodeFromString<BrokerInvocationResult>(
                        ParcelFileCodec.read(output, 64 * 1024)
                    )
                }
            }
        }
        started.await()

        bridge.cancelHttp(requestId.await())

        val result = withTimeout(5_000) { execution.await() }
        val error = (result as BrokerInvocationResult.Failure).error
        assertEquals(BrokerErrorCodes.Cancelled, error.code)
        bridge.close()
    }

    @Test
    fun invalidNullableBrokerRequestClosesItsDescriptor() {
        val fixture = scopeFixture()
        val bridge = bridge(
            broker = RecordingBroker(fixture.store),
            granted = setOf(ExtensionCapabilityIds.Network),
            scope = fixture.scope,
        )
        val request = ParcelFileCodec.write(context, "{}")
        ExtensionResultDispatcher().use { dispatcher ->
            bridge.executeHttp(null, request, dispatcher.callback)
        }

        assertThrows(IOException::class.java) {
            ParcelFileCodec.read(request, 16)
        }
    }

    @Test
    fun brokerFailuresUseTypedCodesAndNeverExposeExceptionMessages() {
        val fixture = scopeFixture()
        val expected = listOf(
            BrokerErrorCodes.Timeout to true,
            BrokerErrorCodes.NetworkFailed to true,
            BrokerErrorCodes.ResponseTooLarge to false,
            BrokerErrorCodes.Internal to true,
        )

        expected.forEach { (code, recoverable) ->
            val bridge = bridge(
                broker = object : ProviderHostNetworkBroker {
                    override suspend fun execute(
                        scope: BrokerScopeHandle,
                        principal: ExtensionPrincipal,
                        hook: Hook,
                        request: BrokeredHttpRequest,
                    ): BrokeredHttpResponse = throw ProviderBrokerException(
                        code = code,
                        recoverable = recoverable,
                        cause = IllegalStateException(SECRET_FAILURE_MESSAGE),
                    )
                },
                granted = setOf(ExtensionCapabilityIds.Network),
                scope = fixture.scope,
            )

            val error = executeFailure(
                bridge,
                BrokeredHttpRequest(method = "GET", url = "$BASE_URL/items"),
            )

            assertEquals(code, error.code)
            assertEquals(recoverable, error.recoverable)
            assertFalse(error.message.contains(SECRET_FAILURE_MESSAGE))
        }
    }

    @Test
    fun oversizedEncodedBrokerEnvelopeBecomesTypedSizeFailure() {
        val fixture = scopeFixture()
        val bridge = bridge(
            broker = object : ProviderHostNetworkBroker {
                override suspend fun execute(
                    scope: BrokerScopeHandle,
                    principal: ExtensionPrincipal,
                    hook: Hook,
                    request: BrokeredHttpRequest,
                ): BrokeredHttpResponse = BrokeredHttpResponse(
                    statusCode = 200,
                    headers = emptyMap(),
                    body = "\u0000".repeat(1024 * 1024),
                )
            },
            granted = setOf(ExtensionCapabilityIds.Network),
            scope = fixture.scope,
        )

        val error = executeFailure(
            bridge,
            BrokeredHttpRequest(method = "GET", url = "$BASE_URL/items"),
        )

        assertEquals(BrokerErrorCodes.ResponseTooLarge, error.code)
        assertFalse(error.recoverable)
        bridge.close()
    }

    @Test
    fun malformedInvocationReturnsSanitizedInvalidRequestEnvelope() {
        val fixture = scopeFixture()
        val bridge = bridge(
            broker = RecordingBroker(fixture.store),
            granted = setOf(ExtensionCapabilityIds.Network),
            scope = fixture.scope,
        )

        val result = executeRaw(
            bridge,
            """{"brokerProtocolVersion":4,"request":{"password":"$SECRET_FAILURE_MESSAGE"}}""",
        ) as BrokerInvocationResult.Failure

        assertEquals(BrokerErrorCodes.InvalidRequest, result.error.code)
        assertFalse(result.error.message.contains(SECRET_FAILURE_MESSAGE))
    }

    @Test
    fun excessiveJsonNestingIsRejectedBeforeBrokerInvocationDecode() {
        val fixture = scopeFixture()
        val broker = RecordingBroker(fixture.store)
        val bridge = bridge(
            broker = broker,
            granted = setOf(ExtensionCapabilityIds.Network),
            scope = fixture.scope,
        )
        val deeplyEncodedUrl = (0 until 80).fold(
            """{"type":"literal","value":"$BASE_URL/items"}"""
        ) { value, _ ->
            """{"type":"encoded","value":$value,"encoding":"base64"}"""
        }
        val result = executeRaw(
            bridge,
            """
            {
              "brokerProtocolVersion": 4,
              "operation": {
                "type": "http",
                "request": {
                  "method": "GET",
                  "url": $deeplyEncodedUrl
                }
              }
            }
            """.trimIndent(),
        ) as BrokerInvocationResult.Failure

        assertEquals(BrokerErrorCodes.InvalidRequest, result.error.code)
        assertEquals(0, broker.calls)
    }

    @Test
    fun brokerRequestQueueIsBoundedPerInvocation() = runBlocking {
        val fixture = scopeFixture()
        val startedCount = AtomicInteger()
        val allStarted = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val broker = object : ProviderHostNetworkBroker {
            override suspend fun execute(
                scope: BrokerScopeHandle,
                principal: ExtensionPrincipal,
                hook: Hook,
                request: BrokeredHttpRequest,
            ): BrokeredHttpResponse {
                fixture.store.authorize(scope, principal, hook)
                if (startedCount.incrementAndGet() == 4) allStarted.complete(Unit)
                release.await()
                return BrokeredHttpResponse(200, emptyMap(), "ok")
            }
        }
        val bridge = bridge(
            broker = broker,
            granted = setOf(ExtensionCapabilityIds.Network),
            scope = fixture.scope,
        )
        val activeRequests = (0 until 4).map { index ->
            async(Dispatchers.IO) {
                execute(
                    bridge,
                    BrokeredHttpRequest(method = "GET", url = "$BASE_URL/items/$index"),
                )
            }
        }
        withTimeout(5_000) { allStarted.await() }

        val overflow = withTimeout(5_000) {
            async(Dispatchers.IO) {
                execute(
                    bridge,
                    BrokeredHttpRequest(method = "GET", url = "$BASE_URL/items/overflow"),
                )
            }.await()
        } as BrokerInvocationResult.Failure

        assertEquals(BrokerErrorCodes.InvalidRequest, overflow.error.code)
        assertEquals(4, startedCount.get())
        release.complete(Unit)
        assertTrue(activeRequests.awaitAll().all { result ->
            result is BrokerInvocationResult.Success
        })
        bridge.close()
    }

    private fun bridge(
        broker: ProviderHostNetworkBroker,
        granted: Set<Capability>,
        scope: BrokerScopeHandle?,
        principal: ExtensionPrincipal = PRINCIPAL,
        hook: Hook = HOOK,
        hookRequiresNetwork: Boolean = true,
    ): ExtensionHostBridge = ExtensionHostBridge(
        context = context,
        broker = broker,
        principal = principal,
        manifest = manifest(hook, hookRequiresNetwork),
        envelope = envelope(granted, scope, hook),
    )

    private fun envelope(
        granted: Set<Capability>,
        scope: BrokerScopeHandle?,
        hook: Hook,
    ) = SerializedExtensionEnvelope(
        apiVersion = ExtensionApiVersion(1, 0),
        invocationId = InvocationId("invocation-1"),
        extensionId = EXTENSION_ID,
        hook = hook,
        schemaVersion = 1,
        payload = JsonObject(emptyMap()),
        grantedCapabilities = granted,
        brokerScope = scope,
    )

    private fun executeSuccess(
        bridge: ExtensionHostBridge,
        request: BrokeredHttpRequest,
    ): BrokeredHttpResponse = (
        (execute(bridge, request) as BrokerInvocationResult.Success).result
            as BrokerOperationResult.Http
        ).response

    private fun executeFailure(
        bridge: ExtensionHostBridge,
        request: BrokeredHttpRequest,
    ): BrokerInvocationError = (execute(bridge, request) as BrokerInvocationResult.Failure).error

    private fun executeAuthenticationFailure(
        bridge: ExtensionHostBridge,
        request: BrokerAuthenticationRequest,
    ): BrokerInvocationError = (
        executeRaw(
            bridge = bridge,
            content = json.encodeToString(
                BrokerInvocation(
                    brokerProtocolVersion = BrokerProtocolVersions.Current,
                    operation = BrokerOperation.Authenticate(request),
                )
            ),
        ) as BrokerInvocationResult.Failure
        ).error

    private fun execute(
        bridge: ExtensionHostBridge,
        request: BrokeredHttpRequest,
    ): BrokerInvocationResult = executeRaw(
        bridge = bridge,
        content = json.encodeToString(
            BrokerInvocation(
                brokerProtocolVersion = BrokerProtocolVersions.Current,
                operation = BrokerOperation.Http(request),
            )
        ),
    )

    private fun executeRaw(
        bridge: ExtensionHostBridge,
        content: String,
    ): BrokerInvocationResult = runBlocking {
        ExtensionResultDispatcher().use { dispatcher ->
            val input = ParcelFileCodec.write(
                context,
                content,
            )
            input.use { request ->
                dispatcher.await { requestId, callback ->
                    bridge.executeHttp(requestId, request, callback)
                }
            }.use { output ->
                json.decodeFromString(ParcelFileCodec.read(output, 64 * 1024))
            }
        }
    }

    private fun scopeFixture(): ScopeFixture {
        val registry = ActiveExtensionPrincipalRegistry()
        registry.activate(PRINCIPAL)
        val store = scopeStore(registry)
        return ScopeFixture(
            store = store,
            scope = store.mintAuthenticationScope(
                principal = PRINCIPAL,
                approvedBaseUrl = BASE_URL,
                transientCredentials = emptyMap(),
            ),
        )
    }

    private fun scopeStore(
        registry: ActiveExtensionPrincipalRegistry,
    ): ProviderBrokerScopeStore {
        var nextId = 0
        return ProviderBrokerScopeStore(
            credentialVault = UnusedCredentialVault,
            principalRegistry = registry,
            clock = { 1_000L },
            idFactory = { "bridge-${nextId++}" },
            defaultTtlMillis = 60_000L,
        )
    }

    private class RecordingBroker(
        private val scopeStore: ProviderBrokerScopeStore,
    ) : ProviderHostNetworkBroker {
        var calls: Int = 0
        var scope: BrokerScopeHandle? = null
        var principal: ExtensionPrincipal? = null
        var hook: Hook? = null

        override suspend fun execute(
            scope: BrokerScopeHandle,
            principal: ExtensionPrincipal,
            hook: Hook,
            request: BrokeredHttpRequest,
        ): BrokeredHttpResponse {
            calls++
            this.scope = scope
            this.principal = principal
            this.hook = hook
            scopeStore.authorize(scope, principal, hook)
            return BrokeredHttpResponse(
                statusCode = 200,
                headers = emptyMap(),
                body = "ok",
            )
        }
    }

    private data class ScopeFixture(
        val store: ProviderBrokerScopeStore,
        val scope: BrokerScopeHandle,
    )

    private object UnusedCredentialVault : CredentialVault {
        override fun encrypt(
            accountId: String,
            secret: String,
            credentialHandle: String?,
        ): ProviderCredentialEntity = error("Not used")

        override fun decrypt(credential: ProviderCredentialEntity): String? = error("Not used")

        override fun stage(secret: String): CredentialHandle = error("Not used")

        override fun consume(handle: CredentialHandle): String? = error("Not used")
    }

    private companion object {
        const val BASE_URL = "https://media.example.test"
        const val SECRET_FAILURE_MESSAGE = "token=must-not-cross-ipc"
        val EXTENSION_ID = ExtensionId("com.example.bridge")
        val HOOK = ExtensionHookIds.SubscriptionProviderValidate
        val PRINCIPAL = ExtensionPrincipal(
            extensionId = EXTENSION_ID,
            packageName = "com.example.bridge",
            serviceName = "com.example.bridge.ExtensionService",
            certificateSha256 = "11".repeat(32),
            uid = 10_001,
        )
        val WRONG_PRINCIPAL = PRINCIPAL.copy(
            packageName = "com.example.bridge.reinstalled",
            serviceName = "com.example.bridge.reinstalled.ExtensionService",
            certificateSha256 = "22".repeat(32),
            uid = 10_002,
        )

        fun manifest(
            hook: Hook,
            hookRequiresNetwork: Boolean = true,
        ): ExtensionManifest {
            val baseCapabilities =
                ExtensionContractCatalog.RequiredCapabilitiesByHook[hook].orEmpty()
            val requiredCapabilities = baseCapabilities + if (hookRequiresNetwork) {
                setOf(ExtensionCapabilityIds.Network)
            } else {
                emptySet()
            }
            val capabilities = mutableSetOf(
                ExtensionCapabilityRequest(ExtensionCapabilityIds.Network, "Fetch provider data"),
                ExtensionCapabilityRequest(
                    ExtensionCapabilityIds.CredentialRead,
                    "Authenticate requests",
                ),
                ExtensionCapabilityRequest(
                    ExtensionCapabilityIds.CredentialWrite,
                    "Store login results",
                ),
            )
            baseCapabilities.forEach { capability ->
                if (capabilities.none { request -> request.capability == capability }) {
                    capabilities += ExtensionCapabilityRequest(
                        capability,
                        "Exercise ${capability.id}",
                    )
                }
            }
            return ExtensionManifest(
                id = EXTENSION_ID,
                displayName = "Bridge test",
                extensionVersion = ExtensionSemanticVersion(1, 0, 0),
                apiRange = ExtensionApiRange(
                    minimum = ExtensionApiVersion(1, 0),
                    maximum = ExtensionApiVersion(1, 0),
                ),
                hooks = setOf(
                    ExtensionHookDeclaration(
                        hook,
                        schemaVersion = 1,
                        requiredCapabilities = requiredCapabilities,
                    )
                ),
                capabilities = capabilities,
            )
        }
    }
}
