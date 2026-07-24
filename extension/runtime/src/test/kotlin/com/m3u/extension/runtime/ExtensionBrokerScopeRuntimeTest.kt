package com.m3u.extension.runtime

import com.m3u.extension.api.ExtensionApiRange
import com.m3u.extension.api.ExtensionApiVersions
import com.m3u.extension.api.ExtensionCapabilityIds
import com.m3u.extension.api.ExtensionCapabilityRequest
import com.m3u.extension.api.ExtensionEntrypoint
import com.m3u.extension.api.ExtensionHandler
import com.m3u.extension.api.ExtensionHookDeclaration
import com.m3u.extension.api.ExtensionHookIds
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.ExtensionManifest
import com.m3u.extension.api.ExtensionPayload
import com.m3u.extension.api.ExtensionSemanticVersion
import com.m3u.extension.api.HookResult
import com.m3u.extension.api.HookSpec
import com.m3u.extension.api.InvocationId
import com.m3u.extension.api.SerializedExtensionEnvelope
import com.m3u.extension.api.SerializedExtensionResult
import com.m3u.extension.api.security.BrokerScopeHandle
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExtensionBrokerScopeRuntimeTest {
    @Test
    fun `external invocation receives and closes a host managed scope`() = runBlocking {
        val lease = TestLease()
        var openedRequest: ExtensionBrokerScopeRequest? = null
        var envelope: SerializedExtensionEnvelope? = null
        val runtime = runtime(
            brokerScopeProvider = ExtensionBrokerScopeProvider { request ->
                openedRequest = request
                lease
            }
        )
        runtime.registerHealthy(transport { request ->
            envelope = request
            request.success(ScopePayload("response"))
        })

        val result = runtime.invoke(EXTENSION_ID, SPEC, ScopePayload("request"))

        assertEquals(
            ScopePayload("response"),
            (result.outcome as HookResult.Success).payload,
        )
        assertEquals(SCOPE, envelope?.brokerScope)
        assertEquals(ScopePayload("request"), openedRequest?.payload)
        assertEquals(SPEC.hook, openedRequest?.hook)
        assertTrue(lease.closed)
    }

    @Test
    fun `explicit provider scope bypasses automatic scope provider`() = runBlocking {
        var openCount = 0
        var receivedScope: BrokerScopeHandle? = null
        val runtime = runtime(
            brokerScopeProvider = ExtensionBrokerScopeProvider {
                openCount += 1
                TestLease()
            }
        )
        runtime.registerHealthy(transport { request ->
            receivedScope = request.brokerScope
            request.success(ScopePayload("response"))
        })

        runtime.invoke(
            extensionId = EXTENSION_ID,
            spec = SPEC,
            request = ScopePayload("request"),
            brokerScope = EXPLICIT_SCOPE,
        )

        assertEquals(0, openCount)
        assertEquals(EXPLICIT_SCOPE, receivedScope)
    }

    @Test
    fun `built in invocation never opens an external broker scope`() = runBlocking {
        var openCount = 0
        val runtime = runtime(
            brokerScopeProvider = ExtensionBrokerScopeProvider {
                openCount += 1
                TestLease()
            }
        )
        runtime.register(entrypoint())

        val result = runtime.invoke(EXTENSION_ID, SPEC, ScopePayload("request"))

        assertEquals(
            ScopePayload("built-in"),
            (result.outcome as HookResult.Success).payload,
        )
        assertEquals(0, openCount)
    }

    @Test
    fun `caller cancellation propagates and closes managed scope`() = runBlocking {
        val lease = TestLease()
        val started = CompletableDeferred<Unit>()
        val runtime = runtime(
            brokerScopeProvider = ExtensionBrokerScopeProvider { lease }
        )
        runtime.registerHealthy(transport {
            started.complete(Unit)
            awaitCancellation()
        })
        val invocation = async {
            runtime.invoke(EXTENSION_ID, SPEC, ScopePayload("request"))
        }
        started.await()

        invocation.cancelAndJoin()

        assertTrue(invocation.isCancelled)
        assertTrue(lease.closed)
    }

    @Test
    fun `scope provider cancellation propagates before transport invocation`() {
        var invoked = false
        val runtime = runtime(
            brokerScopeProvider = ExtensionBrokerScopeProvider {
                throw CancellationException("scope cancelled")
            }
        )
        runtime.registerHealthy(transport { request ->
            invoked = true
            request.success(ScopePayload("unexpected"))
        })

        val failure = assertFailsWith<CancellationException> {
            runBlocking {
                runtime.invoke(EXTENSION_ID, SPEC, ScopePayload("request"))
            }
        }

        assertEquals("scope cancelled", failure.message)
        assertFalse(invoked)
    }

    @Test
    fun `provider may decline a scope without changing the envelope`() = runBlocking {
        val runtime = runtime(
            brokerScopeProvider = ExtensionBrokerScopeProvider { null }
        )
        var receivedScope: BrokerScopeHandle? = SCOPE
        runtime.registerHealthy(transport { request ->
            receivedScope = request.brokerScope
            request.success(ScopePayload("response"))
        })

        runtime.invoke(EXTENSION_ID, SPEC, ScopePayload("request"))

        assertNull(receivedScope)
    }

    @Test
    fun `managed scope opens only after the extension concurrency permit`() = runBlocking {
        var openedScopes = 0
        var invocations = 0
        val firstInvocationStarted = CompletableDeferred<Unit>()
        val releaseFirstInvocation = CompletableDeferred<Unit>()
        val runtime = runtime(
            brokerScopeProvider = ExtensionBrokerScopeProvider {
                openedScopes++
                TestLease()
            },
            invocationPolicy = InvocationPolicy(
                timeoutMillis = 5_000,
                maxConcurrentInvocationsPerExtension = 1,
            ),
        )
        runtime.registerHealthy(
            transport { request ->
                invocations++
                if (invocations == 1) {
                    firstInvocationStarted.complete(Unit)
                    releaseFirstInvocation.await()
                }
                request.success(ScopePayload("response"))
            }
        )

        val first = async {
            runtime.invoke(EXTENSION_ID, SPEC, ScopePayload("first"))
        }
        firstInvocationStarted.await()
        val second = async {
            runtime.invoke(EXTENSION_ID, SPEC, ScopePayload("second"))
        }
        repeat(10) { yield() }

        assertEquals(1, openedScopes)
        releaseFirstInvocation.complete(Unit)
        first.await()
        second.await()
        assertEquals(2, openedScopes)
    }

    private fun ExtensionRuntime.registerHealthy(transport: ExtensionTransport) {
        val registration = assertIs<ExtensionRegistrationResult.Registered>(
            register(transport)
        )
        recordTransportHealth(
            extensionId = transport.manifest.id,
            registrationToken = assertNotNull(registration.registrationToken),
            health = ExtensionTransportHealth.HEALTHY,
        )
    }

    private fun runtime(
        brokerScopeProvider: ExtensionBrokerScopeProvider,
        invocationPolicy: InvocationPolicy = InvocationPolicy(),
    ) = ExtensionRuntime(
        hostApiVersion = ExtensionApiVersions.Current,
        brokerScopeProvider = brokerScopeProvider,
        invocationPolicy = invocationPolicy,
    )

    private fun transport(
        invoke: suspend (SerializedExtensionEnvelope) -> SerializedExtensionResult,
    ) = object : ExtensionTransport {
        override val manifest: ExtensionManifest = MANIFEST

        override suspend fun invoke(
            request: SerializedExtensionEnvelope,
        ): SerializedExtensionResult = invoke(request)

        override suspend fun cancel(invocationId: InvocationId) = Unit

        override suspend fun health(): ExtensionTransportHealth =
            ExtensionTransportHealth.HEALTHY
    }

    private fun entrypoint() = object : ExtensionEntrypoint {
        override val manifest: ExtensionManifest = MANIFEST
        override val handlers: Collection<ExtensionHandler<*, *>> = listOf(
            object : ExtensionHandler<ScopePayload, ScopePayload> {
                override val spec: HookSpec<ScopePayload, ScopePayload> = SPEC

                override suspend fun invoke(
                    context: com.m3u.extension.api.ExtensionCallContext,
                    request: ScopePayload,
                ): HookResult<ScopePayload> = HookResult.Success(ScopePayload("built-in"))
            }
        )
    }

    private fun SerializedExtensionEnvelope.success(
        payload: ScopePayload,
    ) = SerializedExtensionResult(
        invocationId = invocationId,
        extensionId = extensionId,
        hook = hook,
        schemaVersion = schemaVersion,
        payload = JSON.encodeToJsonElement(payload),
    )

    private class TestLease : ExtensionBrokerScopeLease {
        override val handle: BrokerScopeHandle = SCOPE
        var closed: Boolean = false
            private set

        override fun close() {
            check(!closed) { "Scope lease was closed more than once" }
            closed = true
        }
    }

    @Serializable
    private data class ScopePayload(val value: String) : ExtensionPayload

    private companion object {
        val EXTENSION_ID = ExtensionId("com.example.scope")
        val SCOPE = BrokerScopeHandle("managed-scope")
        val EXPLICIT_SCOPE = BrokerScopeHandle("explicit-scope")
        val SPEC = HookSpec(
            hook = ExtensionHookIds.SearchProviderQuery,
            schemaVersion = 4,
            requestSerializer = ScopePayload.serializer(),
            responseSerializer = ScopePayload.serializer(),
        )
        val MANIFEST = ExtensionManifest(
            id = EXTENSION_ID,
            displayName = "Scope example",
            extensionVersion = ExtensionSemanticVersion(1, 0, 0),
            apiRange = ExtensionApiRange(
                minimum = ExtensionApiVersions.Current,
                maximum = ExtensionApiVersions.Current,
            ),
            hooks = setOf(
                ExtensionHookDeclaration(
                    hook = SPEC.hook,
                    schemaVersion = SPEC.schemaVersion,
                    requiredCapabilities = setOf(ExtensionCapabilityIds.SearchRead),
                )
            ),
            capabilities = setOf(
                ExtensionCapabilityRequest(
                    ExtensionCapabilityIds.SearchRead,
                    "Search channels",
                )
            ),
        )
        val JSON = Json
    }
}
