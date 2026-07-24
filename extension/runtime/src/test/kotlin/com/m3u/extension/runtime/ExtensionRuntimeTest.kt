package com.m3u.extension.runtime

import com.m3u.extension.api.EmptyExtensionPayload
import com.m3u.extension.api.Capability
import com.m3u.extension.api.ExtensionApiRange
import com.m3u.extension.api.ExtensionApiVersion
import com.m3u.extension.api.ExtensionApiVersions
import com.m3u.extension.api.ExtensionCallContext
import com.m3u.extension.api.ExtensionCapabilityIds
import com.m3u.extension.api.ExtensionCapabilityRequest
import com.m3u.extension.api.ExtensionError
import com.m3u.extension.api.ExtensionErrorCode
import com.m3u.extension.api.ExtensionErrorCodes
import com.m3u.extension.api.ExtensionHandler
import com.m3u.extension.api.ExtensionHookDeclaration
import com.m3u.extension.api.ExtensionHookIds
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.ExtensionManifest
import com.m3u.extension.api.ExtensionPayload
import com.m3u.extension.api.ExtensionSemanticVersion
import com.m3u.extension.api.ExtensionState
import com.m3u.extension.api.ExtensionSettingsSnapshot
import com.m3u.extension.api.HookResult
import com.m3u.extension.api.HookSpec
import com.m3u.extension.api.InvocationId
import com.m3u.extension.api.SerializedExtensionEnvelope
import com.m3u.extension.api.SerializedExtensionResult
import com.m3u.extension.api.security.BrokerScopeHandle
import com.m3u.extension.api.security.CredentialHandle
import com.m3u.extension.api.ExtensionEntrypoint
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExtensionRuntimeTest {
    @Test
    fun `runtime registers queries and invokes typed hook`() = runBlocking {
        val runtime = runtime()
        val entrypoint = entrypoint()

        val registration = assertIs<ExtensionRegistrationResult.Registered>(
            runtime.register(entrypoint)
        )
        assertNull(registration.registrationToken)
        val registered = runtime.extensionsSupporting(TEST_SPEC.hook).single()
        assertEquals(entrypoint.manifest.id, registered.manifest.id)
        assertEquals(ExtensionExecutionKind.BUILT_IN, registered.executionKind)

        val result = runtime.invoke(entrypoint.manifest.id, TEST_SPEC, TestPayload("stable-reference"))

        assertEquals(InvocationId("invocation-1"), result.invocationId)
        assertEquals(TestPayload("resolved-stable-reference"), assertIs<HookResult.Success<TestPayload>>(result.outcome).payload)
    }

    @Test
    fun `runtime derives and rejects missing capabilities from policy`() = runBlocking {
        val runtime = runtime(capabilityPolicy = CapabilityPolicy { _, _ -> emptySet() })
        val entrypoint = entrypoint()
        runtime.register(entrypoint)

        val result = runtime.invoke(entrypoint.manifest.id, TEST_SPEC, TestPayload("request"))

        assertEquals(
            ExtensionErrorCodes.CapabilityDenied,
            assertIs<HookResult.Failure>(result.outcome).error.code,
        )
    }

    @Test
    fun `runtime rejects incompatible extension API`() {
        val runtime = runtime()
        val entrypoint = entrypoint(
            apiRange = ExtensionApiRange(
                minimum = ExtensionApiVersion(major = 2, minor = 0),
                maximum = ExtensionApiVersion(major = 2, minor = 1),
            )
        )

        val rejected = assertIs<ExtensionRegistrationResult.Rejected>(runtime.register(entrypoint))

        assertEquals(ExtensionErrorCodes.ApiIncompatible, rejected.error.code)
        assertTrue(runtime.registeredExtensions().isEmpty())
    }

    @Test
    fun `runtime negotiates hook schemas across API minor versions`() {
        val runtime = ExtensionRuntime(hostApiVersion = ExtensionApiVersion(major = 1, minor = 9))
        val entrypoint = entrypoint(
            apiRange = ExtensionApiRange(
                minimum = ExtensionApiVersion(major = 1, minor = 0),
                maximum = ExtensionApiVersion(major = 1, minor = 0),
            )
        )

        assertIs<ExtensionRegistrationResult.Registered>(runtime.register(entrypoint))
    }

    @Test
    fun `runtime converts provider exception into safe structured failure`() = runBlocking {
        val runtime = runtime()
        val entrypoint = entrypoint { _, _ -> error("secret provider detail") }
        runtime.register(entrypoint)

        val result = runtime.invoke(entrypoint.manifest.id, TEST_SPEC, TestPayload("request"))

        val failure = assertIs<HookResult.Failure>(result.outcome)
        assertEquals(ExtensionErrorCodes.InvocationFailed, failure.error.code)
        assertEquals("Extension invocation failed", failure.error.message)
        assertEquals("IllegalStateException", failure.error.details["exception"])
    }

    @Test
    fun `runtime times out and quarantines repeatedly failing extension`() = runBlocking {
        val runtime = runtime(
            invocationPolicy = InvocationPolicy(
                timeoutMillis = 10,
                unhealthyFailureThreshold = 1,
            )
        )
        val entrypoint = entrypoint { _, _ ->
            delay(100)
            HookResult.Success(TestPayload("late"))
        }
        runtime.register(entrypoint)

        val timedOut = runtime.invoke(entrypoint.manifest.id, TEST_SPEC, TestPayload("request"))
        assertEquals(ExtensionErrorCodes.InvocationTimedOut, assertIs<HookResult.Failure>(timedOut.outcome).error.code)

        val quarantined = runtime.invoke(entrypoint.manifest.id, TEST_SPEC, TestPayload("request"))
        assertEquals(ExtensionErrorCodes.ExtensionUnhealthy, assertIs<HookResult.Failure>(quarantined.outcome).error.code)
    }

    @Test
    fun `runtime keeps extension enabled below consecutive failure threshold`() = runBlocking {
        val runtime = runtime(invocationPolicy = InvocationPolicy(unhealthyFailureThreshold = 2))
        val entrypoint = entrypoint { _, _ -> error("first failure") }
        runtime.register(entrypoint)

        runtime.invoke(entrypoint.manifest.id, TEST_SPEC, TestPayload("request"))

        assertEquals(ExtensionState.ENABLED, runtime.registeredExtensions().single().state)
        assertEquals(1, runtime.registeredExtensions().single().consecutiveFailures)
    }

    @Test
    fun `external registration rejects invocation until tokenized health succeeds`() = runBlocking {
        val manifest = entrypoint().manifest
        val runtime = runtime()
        val registration = assertIs<ExtensionRegistrationResult.Registered>(
            runtime.register(successfulTransport(manifest))
        )
        val registrationToken = assertNotNull(registration.registrationToken)

        assertEquals(ExtensionState.UNHEALTHY, registration.extension.state)
        assertEquals(ExtensionState.UNHEALTHY, runtime.registeredExtensions().single().state)
        val blocked = runtime.invoke(manifest.id, TEST_SPEC, TestPayload("before-health"))
        assertEquals(
            ExtensionErrorCodes.ExtensionUnhealthy,
            assertIs<HookResult.Failure>(blocked.outcome).error.code,
        )

        runtime.recordTransportHealth(
            manifest.id,
            registrationToken,
            ExtensionTransportHealth.HEALTHY,
        )

        assertEquals(ExtensionState.ENABLED, runtime.registeredExtensions().single().state)
        assertIs<HookResult.Success<TestPayload>>(
            runtime.invoke(manifest.id, TEST_SPEC, TestPayload("after-health")).outcome
        )
        Unit
    }

    @Test
    fun `external transport failure is recoverable and quarantines without cancelling caller`() =
        runBlocking {
            val manifest = entrypoint().manifest
            val runtime = runtime(
                invocationPolicy = InvocationPolicy(unhealthyFailureThreshold = 1)
            )
            val transport = object : ExtensionTransport {
                override val manifest = manifest

                override suspend fun invoke(
                    request: SerializedExtensionEnvelope,
                ): SerializedExtensionResult {
                    throw IllegalStateException("Transport connection was lost")
                }

                override suspend fun cancel(invocationId: InvocationId) = Unit

                override suspend fun health(): ExtensionTransportHealth =
                    ExtensionTransportHealth.UNAVAILABLE
            }
            val registration = assertIs<ExtensionRegistrationResult.Registered>(
                runtime.register(transport)
            )
            val registrationToken = assertNotNull(registration.registrationToken)
            runtime.recordTransportHealth(
                manifest.id,
                registrationToken,
                ExtensionTransportHealth.HEALTHY,
            )

            val result = runtime.invoke(manifest.id, TEST_SPEC, TestPayload("request"))

            val failure = assertIs<HookResult.Failure>(result.outcome).error
            assertEquals(ExtensionErrorCodes.InvocationFailed, failure.code)
            assertTrue(failure.recoverable)
            assertTrue(currentCoroutineContext().isActive)
            assertEquals(1, runtime.registeredExtensions().single().consecutiveFailures)
            assertEquals(ExtensionState.UNHEALTHY, runtime.registeredExtensions().single().state)

            runtime.recordTransportHealth(
                manifest.id,
                registrationToken,
                ExtensionTransportHealth.HEALTHY,
            )
            assertEquals(1, runtime.registeredExtensions().single().consecutiveFailures)
            assertEquals(ExtensionState.UNHEALTHY, runtime.registeredExtensions().single().state)

            runtime.setEnabled(manifest.id, enabled = true)
            assertEquals(0, runtime.registeredExtensions().single().consecutiveFailures)
            assertEquals(ExtensionState.ENABLED, runtime.registeredExtensions().single().state)
        }

    @Test
    fun `external transport health can quarantine and recover a registration`() = runBlocking {
        val manifest = entrypoint().manifest
        val json = Json
        val runtime = runtime()
        val transport = object : ExtensionTransport {
            override val manifest = manifest

            override suspend fun invoke(
                request: SerializedExtensionEnvelope,
            ) = SerializedExtensionResult(
                invocationId = request.invocationId,
                extensionId = request.extensionId,
                hook = request.hook,
                schemaVersion = request.schemaVersion,
                payload = json.encodeToJsonElement(
                    TEST_SPEC.responseSerializer,
                    TestPayload("healthy"),
                ),
            )

            override suspend fun cancel(invocationId: InvocationId) = Unit

            override suspend fun health(): ExtensionTransportHealth =
                ExtensionTransportHealth.HEALTHY
        }
        val registration = assertIs<ExtensionRegistrationResult.Registered>(
            runtime.register(transport)
        )
        val registrationToken = assertNotNull(registration.registrationToken)

        runtime.recordTransportHealth(
            manifest.id,
            registrationToken,
            ExtensionTransportHealth.DEGRADED,
        )
        assertEquals(ExtensionState.UNHEALTHY, runtime.registeredExtensions().single().state)
        val quarantined = runtime.invoke(manifest.id, TEST_SPEC, TestPayload("request"))
        assertEquals(
            ExtensionErrorCodes.ExtensionUnhealthy,
            assertIs<HookResult.Failure>(quarantined.outcome).error.code,
        )

        runtime.recordTransportHealth(
            manifest.id,
            registrationToken,
            ExtensionTransportHealth.HEALTHY,
        )
        assertEquals(ExtensionState.ENABLED, runtime.registeredExtensions().single().state)
        assertEquals(
            TestPayload("healthy"),
            assertIs<HookResult.Success<TestPayload>>(
                runtime.invoke(manifest.id, TEST_SPEC, TestPayload("request")).outcome
            ).payload,
        )
    }

    @Test
    fun `stale transport health cannot update a replacement registration`() {
        val manifest = entrypoint().manifest
        val runtime = runtime()
        val first = assertIs<ExtensionRegistrationResult.Registered>(
            runtime.register(transport(manifest))
        )
        val firstToken = assertNotNull(first.registrationToken)
        runtime.unregister(manifest.id)
        val replacement = assertIs<ExtensionRegistrationResult.Registered>(
            runtime.register(transport(manifest))
        )
        val replacementToken = assertNotNull(replacement.registrationToken)

        assertNull(
            runtime.recordTransportHealth(
                manifest.id,
                firstToken,
                ExtensionTransportHealth.UNAVAILABLE,
            )
        )
        assertEquals(ExtensionState.UNHEALTHY, runtime.registeredExtensions().single().state)

        runtime.recordTransportHealth(
            manifest.id,
            replacementToken,
            ExtensionTransportHealth.UNAVAILABLE,
        )
        assertEquals(ExtensionState.UNHEALTHY, runtime.registeredExtensions().single().state)
    }

    @Test
    fun `external failures survive reconnect until explicit enable resets them`() = runBlocking {
        val manifest = entrypoint().manifest
        val runtime = runtime(
            invocationPolicy = InvocationPolicy(unhealthyFailureThreshold = 2)
        )
        val first = assertIs<ExtensionRegistrationResult.Registered>(
            runtime.register(throwingTransport(manifest))
        )
        runtime.recordTransportHealth(
            manifest.id,
            assertNotNull(first.registrationToken),
            ExtensionTransportHealth.HEALTHY,
        )

        runtime.invoke(manifest.id, TEST_SPEC, TestPayload("first crash"))
        assertEquals(1, runtime.registeredExtensions().single().consecutiveFailures)
        runtime.unregister(manifest.id)

        val replacement = assertIs<ExtensionRegistrationResult.Registered>(
            runtime.register(throwingTransport(manifest))
        )
        val replacementToken = assertNotNull(replacement.registrationToken)
        assertEquals(1, runtime.registeredExtensions().single().consecutiveFailures)
        runtime.recordTransportHealth(
            manifest.id,
            replacementToken,
            ExtensionTransportHealth.HEALTHY,
        )
        assertEquals(1, runtime.registeredExtensions().single().consecutiveFailures)

        runtime.invoke(manifest.id, TEST_SPEC, TestPayload("second crash"))
        assertEquals(2, runtime.registeredExtensions().single().consecutiveFailures)
        assertEquals(ExtensionState.UNHEALTHY, runtime.registeredExtensions().single().state)
        runtime.unregister(manifest.id)

        val quarantinedReplacement = assertIs<ExtensionRegistrationResult.Registered>(
            runtime.register(transport(manifest))
        )
        val quarantinedToken = assertNotNull(quarantinedReplacement.registrationToken)
        runtime.recordTransportHealth(
            manifest.id,
            quarantinedToken,
            ExtensionTransportHealth.HEALTHY,
        )
        assertEquals(2, runtime.registeredExtensions().single().consecutiveFailures)
        assertEquals(ExtensionState.UNHEALTHY, runtime.registeredExtensions().single().state)

        runtime.setEnabled(manifest.id, enabled = true)
        assertEquals(0, runtime.registeredExtensions().single().consecutiveFailures)
        assertEquals(ExtensionState.ENABLED, runtime.registeredExtensions().single().state)
    }

    @Test
    fun `successful external invocation after reconnect clears prior failure`() = runBlocking {
        val manifest = entrypoint().manifest
        val runtime = runtime(
            invocationPolicy = InvocationPolicy(unhealthyFailureThreshold = 2)
        )
        val first = assertIs<ExtensionRegistrationResult.Registered>(
            runtime.register(throwingTransport(manifest))
        )
        runtime.recordTransportHealth(
            manifest.id,
            assertNotNull(first.registrationToken),
            ExtensionTransportHealth.HEALTHY,
        )
        runtime.invoke(manifest.id, TEST_SPEC, TestPayload("first crash"))
        assertEquals(1, runtime.registeredExtensions().single().consecutiveFailures)
        runtime.unregister(manifest.id)

        val replacement = assertIs<ExtensionRegistrationResult.Registered>(
            runtime.register(successfulTransport(manifest))
        )
        runtime.recordTransportHealth(
            manifest.id,
            assertNotNull(replacement.registrationToken),
            ExtensionTransportHealth.HEALTHY,
        )
        val result = runtime.invoke(manifest.id, TEST_SPEC, TestPayload("recovered"))

        assertIs<HookResult.Success<TestPayload>>(result.outcome)
        assertEquals(0, runtime.registeredExtensions().single().consecutiveFailures)
        assertEquals(ExtensionState.ENABLED, runtime.registeredExtensions().single().state)
    }

    @Test
    fun `external failure count saturates instead of overflowing`() {
        assertEquals(
            Int.MAX_VALUE,
            incrementSaturatedFailureCount(Int.MAX_VALUE),
        )
        assertEquals(
            Int.MAX_VALUE,
            incrementSaturatedFailureCount(Int.MAX_VALUE - 1),
        )
    }

    @Test
    fun `external state can only be forgotten after unregister`() = runBlocking {
        val manifest = entrypoint().manifest
        val runtime = runtime(
            invocationPolicy = InvocationPolicy(unhealthyFailureThreshold = 2)
        )
        val registration = assertIs<ExtensionRegistrationResult.Registered>(
            runtime.register(throwingTransport(manifest))
        )
        runtime.recordTransportHealth(
            manifest.id,
            assertNotNull(registration.registrationToken),
            ExtensionTransportHealth.HEALTHY,
        )
        runtime.invoke(manifest.id, TEST_SPEC, TestPayload("first crash"))
        assertEquals(1, runtime.registeredExtensions().single().consecutiveFailures)

        assertEquals(false, runtime.forgetExternalState(manifest.id))
        assertEquals(1, runtime.registeredExtensions().single().consecutiveFailures)

        runtime.unregister(manifest.id)
        assertEquals(true, runtime.forgetExternalState(manifest.id))
        assertEquals(false, runtime.forgetExternalState(manifest.id))

        val replacement = assertIs<ExtensionRegistrationResult.Registered>(
            runtime.register(transport(manifest))
        )
        assertEquals(0, runtime.registeredExtensions().single().consecutiveFailures)
        assertEquals(ExtensionState.UNHEALTHY, runtime.registeredExtensions().single().state)
        runtime.recordTransportHealth(
            manifest.id,
            assertNotNull(replacement.registrationToken),
            ExtensionTransportHealth.HEALTHY,
        )
        assertEquals(ExtensionState.ENABLED, runtime.registeredExtensions().single().state)
    }

    @Test
    fun `semantic response violations count toward quarantine`() = runBlocking {
        val runtime = runtime(invocationPolicy = InvocationPolicy(unhealthyFailureThreshold = 2))
        val entrypoint = entrypoint { _, _ -> HookResult.Success(TestPayload("invalid")) }
        runtime.register(entrypoint)

        repeat(2) {
            val result = runtime.invoke(
                extensionId = entrypoint.manifest.id,
                spec = TEST_SPEC,
                request = TestPayload("request"),
                validateResponse = { response ->
                    require(response.value == "valid")
                },
            )
            assertEquals(
                ExtensionErrorCodes.ResponseInvalid,
                assertIs<HookResult.Failure>(result.outcome).error.code,
            )
        }

        val quarantined = runtime.invoke(
            extensionId = entrypoint.manifest.id,
            spec = TEST_SPEC,
            request = TestPayload("request"),
        )
        assertEquals(
            ExtensionErrorCodes.ExtensionUnhealthy,
            assertIs<HookResult.Failure>(quarantined.outcome).error.code,
        )
    }

    @Test
    fun `structured hook failures do not make an external extension unhealthy`() = runBlocking {
        val runtime = runtime(invocationPolicy = InvocationPolicy(unhealthyFailureThreshold = 1))
        val manifest = entrypoint().manifest
        val authenticationFailure = ExtensionError(
            code = ExtensionErrorCode("provider.authentication_failed"),
            message = "Credentials were rejected",
            recoverable = false,
        )
        val transport = object : ExtensionTransport {
            override val manifest = manifest

            override suspend fun invoke(request: SerializedExtensionEnvelope) = SerializedExtensionResult(
                invocationId = request.invocationId,
                extensionId = request.extensionId,
                hook = request.hook,
                schemaVersion = request.schemaVersion,
                error = authenticationFailure,
            )

            override suspend fun cancel(invocationId: InvocationId) = Unit
            override suspend fun health(): ExtensionTransportHealth = ExtensionTransportHealth.HEALTHY
        }
        val registration = assertIs<ExtensionRegistrationResult.Registered>(
            runtime.register(transport)
        )
        runtime.recordTransportHealth(
            manifest.id,
            assertNotNull(registration.registrationToken),
            ExtensionTransportHealth.HEALTHY,
        )

        repeat(2) {
            val result = runtime.invoke(manifest.id, TEST_SPEC, TestPayload("wrong-password"))
            assertEquals(
                authenticationFailure.code,
                assertIs<HookResult.Failure>(result.outcome).error.code,
            )
        }

        assertEquals(ExtensionState.ENABLED, runtime.registeredExtensions().single().state)
        assertEquals(0, runtime.registeredExtensions().single().consecutiveFailures)
    }

    @Test
    fun `runtime rejects oversized response`() = runBlocking {
        val runtime = runtime(invocationPolicy = InvocationPolicy(maxPayloadBytes = 64))
        val entrypoint = entrypoint { _, _ -> HookResult.Success(TestPayload("x".repeat(100))) }
        runtime.register(entrypoint)

        val result = runtime.invoke(entrypoint.manifest.id, TEST_SPEC, TestPayload("small"))

        assertEquals(
            ExtensionErrorCodes.PayloadTooLarge,
            assertIs<HookResult.Failure>(result.outcome).error.code,
        )
    }

    @Test
    fun `runtime rejects oversized external failure message and details`() = runBlocking {
        val manifest = entrypoint().manifest
        val oversizedFailures = listOf(
            ExtensionError(
                code = ExtensionErrorCode("provider.authentication_failed"),
                message = "token=${"x".repeat(4_096)}",
                recoverable = true,
            ),
            ExtensionError(
                code = ExtensionErrorCode("provider.authentication_failed"),
                message = "Authentication failed",
                recoverable = true,
                details = mapOf("context" to "authorization=${"x".repeat(4_096)}"),
            ),
        )

        oversizedFailures.forEach { oversizedFailure ->
            val runtime = runtime(
                invocationPolicy = InvocationPolicy(
                    maxPayloadBytes = 256,
                    unhealthyFailureThreshold = 1,
                )
            )
            val registration = assertIs<ExtensionRegistrationResult.Registered>(
                runtime.register(failingTransport(manifest, oversizedFailure))
            )
            runtime.recordTransportHealth(
                manifest.id,
                assertNotNull(registration.registrationToken),
                ExtensionTransportHealth.HEALTHY,
            )

            val result = runtime.invoke(manifest.id, TEST_SPEC, TestPayload("request"))
            val failure = assertIs<HookResult.Failure>(result.outcome).error

            assertEquals(ExtensionErrorCodes.PayloadTooLarge, failure.code)
            assertEquals("Extension response exceeds the host limit", failure.message)
            assertEquals(false, failure.recoverable)
            assertTrue(failure.details.isEmpty())
            assertEquals(ExtensionState.UNHEALTHY, runtime.registeredExtensions().single().state)
            assertEquals(1, runtime.registeredExtensions().single().consecutiveFailures)
        }
    }

    @Test
    fun `runtime preserves and sanitizes external failure at payload limit`() = runBlocking {
        val manifest = entrypoint().manifest
        val rawFailure = ExtensionError(
            code = ExtensionErrorCode("provider.authentication_failed"),
            message = "Credentials rejected; token=top-secret",
            recoverable = true,
            details = linkedMapOf(
                "password" to "not-visible",
                "reason" to "authorization=Bearer-secret",
                "retry" to "allowed",
            ),
        )
        val encodedSize = Json.encodeToString(ExtensionError.serializer(), rawFailure)
            .encodeToByteArray().size
        val runtime = runtime(
            invocationPolicy = InvocationPolicy(
                maxPayloadBytes = encodedSize,
                unhealthyFailureThreshold = 1,
            )
        )
        val registration = assertIs<ExtensionRegistrationResult.Registered>(
            runtime.register(failingTransport(manifest, rawFailure))
        )
        runtime.recordTransportHealth(
            manifest.id,
            assertNotNull(registration.registrationToken),
            ExtensionTransportHealth.HEALTHY,
        )

        val result = runtime.invoke(manifest.id, TEST_SPEC, TestPayload("request"))
        val failure = assertIs<HookResult.Failure>(result.outcome).error

        assertEquals(rawFailure.code, failure.code)
        assertEquals("Credentials rejected; token=<redacted>", failure.message)
        assertEquals(true, failure.recoverable)
        assertEquals(
            mapOf(
                "reason" to "authorization=<redacted>",
                "retry" to "allowed",
            ),
            failure.details,
        )
        assertEquals(ExtensionState.ENABLED, runtime.registeredExtensions().single().state)
        assertEquals(0, runtime.registeredExtensions().single().consecutiveFailures)
    }

    @Test
    fun `serialized transport conforms to typed invocation behavior`() = runBlocking {
        val settings = ExtensionSettingsSnapshot(
            schemaVersions = mapOf("general" to 2),
            values = mapOf("general.enabled" to JsonPrimitive(true)),
            credentialHandles = mapOf(
                "general.password" to CredentialHandle("extension-secret:opaque"),
            ),
        )
        val runtime = runtime(settingsProvider = ExtensionSettingsProvider { settings })
        val manifest = entrypoint().manifest
        val json = Json
        val brokerScope = BrokerScopeHandle("broker-scope-1")
        val transport = object : ExtensionTransport {
            override val manifest = manifest

            override suspend fun invoke(request: SerializedExtensionEnvelope): SerializedExtensionResult {
                assertEquals(settings, request.settings)
                assertEquals(setOf(ExtensionCapabilityIds.PlaybackResolve), request.grantedCapabilities)
                assertEquals(brokerScope, request.brokerScope)
                val payload = json.decodeFromJsonElement(TEST_SPEC.requestSerializer, request.payload)
                return SerializedExtensionResult(
                    invocationId = request.invocationId,
                    extensionId = request.extensionId,
                    hook = request.hook,
                    schemaVersion = request.schemaVersion,
                    payload = json.encodeToJsonElement(
                        TEST_SPEC.responseSerializer,
                        TestPayload("resolved-${payload.value}"),
                    ),
                )
            }

            override suspend fun cancel(invocationId: InvocationId) = Unit
            override suspend fun health(): ExtensionTransportHealth = ExtensionTransportHealth.HEALTHY
        }
        val registration = assertIs<ExtensionRegistrationResult.Registered>(runtime.register(transport))
        assertEquals(ExtensionExecutionKind.EXTERNAL, registration.extension.executionKind)
        runtime.recordTransportHealth(
            manifest.id,
            assertNotNull(registration.registrationToken),
            ExtensionTransportHealth.HEALTHY,
        )

        val result = runtime.invoke(
            extensionId = manifest.id,
            spec = TEST_SPEC,
            request = TestPayload("transport"),
            brokerScope = brokerScope,
        )

        assertEquals(
            TestPayload("resolved-transport"),
            assertIs<HookResult.Success<TestPayload>>(result.outcome).payload,
        )
    }

    @Test
    fun `runtime passes host settings to built-in handler context`() = runBlocking {
        val settings = ExtensionSettingsSnapshot(
            schemaVersions = mapOf("manifest" to 1),
            values = mapOf("manifest.enabled" to JsonPrimitive(false)),
        )
        var received: ExtensionSettingsSnapshot? = null
        val runtime = runtime(settingsProvider = ExtensionSettingsProvider { settings })
        val entrypoint = entrypoint { context, payload ->
            received = context.settings
            HookResult.Success(payload)
        }
        runtime.register(entrypoint)

        runtime.invoke(entrypoint.manifest.id, TEST_SPEC, TestPayload("request"))

        assertEquals(settings, received)
    }

    @Test
    fun `invocation exposes only capabilities required by the selected hook`() = runBlocking {
        var received: Set<Capability>? = null
        val base = entrypoint { context, payload ->
            received = context.grantedCapabilities
            HookResult.Success(payload)
        }
        val scopedEntrypoint = object : ExtensionEntrypoint {
            override val manifest = base.manifest.copy(
                capabilities = base.manifest.capabilities +
                    ExtensionCapabilityRequest(
                        ExtensionCapabilityIds.SearchRead,
                        "Search channels in another hook",
                    )
            )
            override val handlers = base.handlers
        }
        val runtime = runtime()
        runtime.register(scopedEntrypoint)

        runtime.invoke(
            scopedEntrypoint.manifest.id,
            TEST_SPEC,
            TestPayload("request"),
        )

        assertEquals(setOf(ExtensionCapabilityIds.PlaybackResolve), received)
    }

    @Test
    fun `settings count toward invocation payload limit`() = runBlocking {
        val runtime = runtime(
            invocationPolicy = InvocationPolicy(maxPayloadBytes = 128),
            settingsProvider = ExtensionSettingsProvider {
                ExtensionSettingsSnapshot(
                    values = mapOf("manifest.value" to JsonPrimitive("x".repeat(256))),
                )
            },
        )
        val entrypoint = entrypoint()
        runtime.register(entrypoint)

        val result = runtime.invoke(entrypoint.manifest.id, TEST_SPEC, TestPayload("small"))

        assertEquals(
            ExtensionErrorCodes.PayloadTooLarge,
            assertIs<HookResult.Failure>(result.outcome).error.code,
        )
    }

    @Test
    fun `external transport rejects unsupported hook schema`() {
        val baseManifest = entrypoint().manifest
        val manifest = baseManifest.copy(
            hooks = setOf(baseManifest.hooks.single().copy(schemaVersion = 99))
        )

        val rejected = assertIs<ExtensionRegistrationResult.Rejected>(
            runtime().register(transport(manifest))
        )

        assertEquals(ExtensionErrorCodes.SchemaIncompatible, rejected.error.code)
    }

    @Test
    fun `external transport rejects unknown required capability but ignores optional one`() {
        val unknown = Capability("future.capability")
        val requiredManifest = entrypoint().manifest.copy(
            capabilities = entrypoint().manifest.capabilities +
                ExtensionCapabilityRequest(unknown, "A future host service", required = true)
        )
        val optionalManifest = requiredManifest.copy(
            capabilities = requiredManifest.capabilities.mapTo(mutableSetOf()) { request ->
                if (request.capability == unknown) request.copy(required = false) else request
            }
        )

        val rejected = assertIs<ExtensionRegistrationResult.Rejected>(
            runtime().register(transport(requiredManifest))
        )
        assertEquals(ExtensionErrorCodes.CapabilityDenied, rejected.error.code)
        assertIs<ExtensionRegistrationResult.Registered>(runtime().register(transport(optionalManifest)))
    }

    @Test
    fun `runtime rejects oversized built in manifest before registration`() {
        val original = entrypoint()
        val oversized = object : ExtensionEntrypoint {
            override val manifest = original.manifest.copy(displayName = "x".repeat(161))
            override val handlers = original.handlers
        }

        val rejected = assertIs<ExtensionRegistrationResult.Rejected>(
            runtime().register(oversized)
        )

        assertEquals(ExtensionErrorCodes.RegistrationInvalid, rejected.error.code)
        assertTrue(runtime().registeredExtensions().isEmpty())
    }

    @Test
    fun `runtime rejects oversized external capability reason`() {
        val original = entrypoint().manifest
        val oversized = original.copy(
            capabilities = original.capabilities.mapTo(mutableSetOf()) { request ->
                request.copy(reason = "x".repeat(1_025))
            }
        )

        val rejected = assertIs<ExtensionRegistrationResult.Rejected>(
            runtime().register(transport(oversized))
        )

        assertEquals(ExtensionErrorCodes.RegistrationInvalid, rejected.error.code)
    }

    @Test
    fun `runtime rejects manifest text that can spoof permission UI`() {
        listOf("Trusted\nCertificate: forged", "Trusted\u202Eforged").forEach { displayName ->
            val rejected = assertIs<ExtensionRegistrationResult.Rejected>(
                runtime().register(
                    transport(entrypoint().manifest.copy(displayName = displayName))
                )
            )

            assertEquals(ExtensionErrorCodes.RegistrationInvalid, rejected.error.code)
        }
    }

    @Test
    fun `runtime quarantines unsafe external error text`() = runBlocking {
        val runtime = runtime(
            invocationPolicy = InvocationPolicy(unhealthyFailureThreshold = 1)
        )
        val manifest = entrypoint().manifest
        val registration = assertIs<ExtensionRegistrationResult.Registered>(
            runtime.register(
                failingTransport(
                    manifest,
                    ExtensionError(
                        code = ExtensionErrorCode("provider.failed"),
                        message = "Try again\nCertificate: forged",
                        recoverable = true,
                    ),
                )
            )
        )
        runtime.recordTransportHealth(
            manifest.id,
            assertNotNull(registration.registrationToken),
            ExtensionTransportHealth.HEALTHY,
        )

        val result = runtime.invoke(manifest.id, TEST_SPEC, TestPayload("request"))

        assertEquals(
            ExtensionErrorCodes.ResponseInvalid,
            assertIs<HookResult.Failure>(result.outcome).error.code,
        )
        assertEquals(ExtensionState.UNHEALTHY, runtime.registeredExtensions().single().state)
    }

    private fun transport(extensionManifest: ExtensionManifest) = object : ExtensionTransport {
        override val manifest = extensionManifest
        override suspend fun invoke(request: SerializedExtensionEnvelope): SerializedExtensionResult =
            error("Not invoked")
        override suspend fun cancel(invocationId: InvocationId) = Unit
        override suspend fun health(): ExtensionTransportHealth = ExtensionTransportHealth.HEALTHY
    }

    private fun throwingTransport(
        extensionManifest: ExtensionManifest,
    ) = object : ExtensionTransport {
        override val manifest = extensionManifest

        override suspend fun invoke(
            request: SerializedExtensionEnvelope,
        ): SerializedExtensionResult = error("Transport connection was lost")

        override suspend fun cancel(invocationId: InvocationId) = Unit

        override suspend fun health(): ExtensionTransportHealth =
            ExtensionTransportHealth.UNAVAILABLE
    }

    private fun successfulTransport(
        extensionManifest: ExtensionManifest,
    ) = object : ExtensionTransport {
        override val manifest = extensionManifest

        override suspend fun invoke(
            request: SerializedExtensionEnvelope,
        ) = SerializedExtensionResult(
            invocationId = request.invocationId,
            extensionId = request.extensionId,
            hook = request.hook,
            schemaVersion = request.schemaVersion,
            payload = Json.encodeToJsonElement(
                TEST_SPEC.responseSerializer,
                TestPayload("healthy"),
            ),
        )

        override suspend fun cancel(invocationId: InvocationId) = Unit

        override suspend fun health(): ExtensionTransportHealth =
            ExtensionTransportHealth.HEALTHY
    }

    private fun failingTransport(
        extensionManifest: ExtensionManifest,
        error: ExtensionError,
    ) = object : ExtensionTransport {
        override val manifest = extensionManifest

        override suspend fun invoke(request: SerializedExtensionEnvelope) = SerializedExtensionResult(
            invocationId = request.invocationId,
            extensionId = request.extensionId,
            hook = request.hook,
            schemaVersion = request.schemaVersion,
            error = error,
        )

        override suspend fun cancel(invocationId: InvocationId) = Unit
        override suspend fun health(): ExtensionTransportHealth = ExtensionTransportHealth.HEALTHY
    }

    private fun runtime(
        capabilityPolicy: CapabilityPolicy = DeclaredCapabilityPolicy,
        invocationPolicy: InvocationPolicy = InvocationPolicy(),
        settingsProvider: ExtensionSettingsProvider = EmptyExtensionSettingsProvider,
    ) = ExtensionRuntime(
        hostApiVersion = ExtensionApiVersions.Current,
        invocationIdFactory = InvocationIdFactory { InvocationId("invocation-1") },
        capabilityPolicy = capabilityPolicy,
        settingsProvider = settingsProvider,
        invocationPolicy = invocationPolicy,
    )

    private fun entrypoint(
        apiRange: ExtensionApiRange = ExtensionApiRange(
            minimum = ExtensionApiVersions.Current,
            maximum = ExtensionApiVersions.Current,
        ),
        invoke: suspend (ExtensionCallContext, TestPayload) -> HookResult<TestPayload> = { _, payload ->
            HookResult.Success(TestPayload("resolved-${payload.value}"))
        },
    ): ExtensionEntrypoint = object : ExtensionEntrypoint {
        override val manifest = ExtensionManifest(
            id = ExtensionId("com.example.provider"),
            displayName = "Example Provider",
            extensionVersion = ExtensionSemanticVersion(1, 0, 0),
            apiRange = apiRange,
            hooks = setOf(
                ExtensionHookDeclaration(
                    hook = TEST_SPEC.hook,
                    schemaVersion = TEST_SPEC.schemaVersion,
                    requiredCapabilities = setOf(ExtensionCapabilityIds.PlaybackResolve),
                )
            ),
            capabilities = setOf(
                ExtensionCapabilityRequest(
                    capability = ExtensionCapabilityIds.PlaybackResolve,
                    reason = "Resolve playback references",
                )
            ),
        )
        override val handlers: Collection<ExtensionHandler<*, *>> = listOf(
            object : ExtensionHandler<TestPayload, TestPayload> {
                override val spec = TEST_SPEC
                override suspend fun invoke(context: ExtensionCallContext, request: TestPayload) = invoke(context, request)
            }
        )
    }

    @Serializable
    private data class TestPayload(val value: String) : ExtensionPayload

    private companion object {
        val TEST_SPEC = HookSpec(
            hook = ExtensionHookIds.PlaybackSourceResolve,
            schemaVersion = 4,
            requestSerializer = TestPayload.serializer(),
            responseSerializer = TestPayload.serializer(),
        )
    }
}
