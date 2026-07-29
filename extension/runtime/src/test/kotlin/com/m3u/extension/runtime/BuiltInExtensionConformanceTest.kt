package com.m3u.extension.runtime

import com.m3u.extension.api.BackgroundTaskRequest
import com.m3u.extension.api.BackgroundTaskResult
import com.m3u.extension.api.ExtensionApiVersions
import com.m3u.extension.api.ExtensionCallContext
import com.m3u.extension.api.ExtensionEntrypoint
import com.m3u.extension.api.ExtensionHandler
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.ExtensionManifest
import com.m3u.extension.api.ExtensionSettingsSnapshot
import com.m3u.extension.api.HookResult
import com.m3u.extension.api.HookSpec
import com.m3u.extension.api.HostHookSpecs
import com.m3u.extension.api.InvocationId
import com.m3u.extension.api.SerializedExtensionEnvelope
import com.m3u.extension.api.SerializedExtensionResult
import com.m3u.extension.conformance.ExtensionConformanceDriver
import com.m3u.extension.conformance.ExtensionConformanceFixtureState
import com.m3u.extension.conformance.ExtensionConformanceFixtures
import com.m3u.extension.conformance.ExtensionConformanceSuite
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.test.Test
import kotlin.test.assertIs

class BuiltInExtensionConformanceTest {
    @Test
    fun `built in runtime passes the shared conformance suite`() = runBlocking {
        val manifest = ExtensionConformanceFixtures.manifest(
            id = ExtensionId("com.m3u.testing.conformance.builtin"),
            displayName = "Built-in conformance fixture",
        )
        val fixtureState = ExtensionConformanceFixtureState()

        ExtensionConformanceSuite().verifyStandardBehavior(
            BuiltInConformanceDriver(
                manifest = manifest,
                fixtureState = fixtureState,
            )
        )
    }
}

private class BuiltInConformanceDriver(
    override val manifest: ExtensionManifest,
    fixtureState: ExtensionConformanceFixtureState,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    },
) : ExtensionConformanceDriver {
    private val activeInvocations = ConcurrentHashMap<InvocationId, Job>()
    private val entrypoint = object : ExtensionEntrypoint {
        override val manifest: ExtensionManifest = this@BuiltInConformanceDriver.manifest
        override val handlers: Collection<ExtensionHandler<*, *>> = listOf(
            object : ExtensionHandler<BackgroundTaskRequest, BackgroundTaskResult> {
                override val spec = HostHookSpecs.BackgroundTask

                override suspend fun invoke(
                    context: ExtensionCallContext,
                    request: BackgroundTaskRequest,
                ): HookResult<BackgroundTaskResult> =
                    HookResult.Success(fixtureState.handle(request, context))
            }
        )
    }

    override suspend fun invoke(
        envelope: SerializedExtensionEnvelope,
    ): SerializedExtensionResult {
        val job = checkNotNull(currentCoroutineContext()[Job])
        check(activeInvocations.putIfAbsent(envelope.invocationId, job) == null) {
            "Conformance invocation id is already active"
        }
        return try {
            val requestedBudget = envelope.invocationBudget
            val runtime = ExtensionRuntime(
                hostApiVersion = ExtensionApiVersions.Current,
                invocationIdFactory = InvocationIdFactory { envelope.invocationId },
                capabilityPolicy = CapabilityPolicy { _, _ ->
                    envelope.grantedCapabilities
                },
                settingsProvider = object : ExtensionSettingsProvider {
                    override fun snapshot(
                        manifest: ExtensionManifest,
                    ): ExtensionSettingsSnapshot = envelope.settings
                },
                invocationPolicy = InvocationPolicy(
                    timeoutMillis = requestedBudget.remainingTimeMillis,
                    maxBrokerRequestsPerInvocation = requestedBudget.maxBrokerRequests,
                    maxBrokerRequestBytesPerInvocation = requestedBudget.maxBrokerRequestBytes,
                    maxBrokerResponseBytesPerInvocation = requestedBudget.maxBrokerResponseBytes,
                ),
                json = json,
            )
            assertIs<ExtensionRegistrationResult.Registered>(runtime.register(entrypoint))
            val canonicalSpec = HostHookSpecs.BackgroundTask
            check(envelope.hook == canonicalSpec.hook) {
                "Built-in conformance driver only supports ${canonicalSpec.hook.id}"
            }
            val invocationSpec = if (envelope.schemaVersion == canonicalSpec.schemaVersion) {
                canonicalSpec
            } else {
                HookSpec(
                    hook = envelope.hook,
                    schemaVersion = envelope.schemaVersion,
                    requestSerializer = canonicalSpec.requestSerializer,
                    responseSerializer = canonicalSpec.responseSerializer,
                )
            }
            val request = json.decodeFromJsonElement(
                canonicalSpec.requestSerializer,
                envelope.payload,
            )
            val result = runtime.invoke(
                extensionId = envelope.extensionId,
                spec = invocationSpec,
                request = request,
            )
            when (val outcome = result.outcome) {
                is HookResult.Success -> SerializedExtensionResult(
                    invocationId = result.invocationId,
                    extensionId = result.extensionId,
                    hook = result.spec.hook,
                    schemaVersion = result.spec.schemaVersion,
                    payload = json.encodeToJsonElement(
                        invocationSpec.responseSerializer,
                        outcome.payload,
                    ),
                )
                is HookResult.Failure -> SerializedExtensionResult(
                    invocationId = result.invocationId,
                    extensionId = result.extensionId,
                    hook = result.spec.hook,
                    schemaVersion = result.spec.schemaVersion,
                    error = outcome.error,
                )
            }
        } finally {
            activeInvocations.remove(envelope.invocationId, job)
        }
    }

    override suspend fun cancel(invocationId: InvocationId) {
        activeInvocations[invocationId]?.cancel()
    }

    private companion object {
    }
}
