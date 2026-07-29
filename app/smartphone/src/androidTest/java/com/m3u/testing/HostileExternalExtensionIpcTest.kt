package com.m3u.testing

import android.content.Context
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.m3u.extension.api.BackgroundTaskRequest
import com.m3u.extension.api.BackgroundTaskResult
import com.m3u.extension.api.ExtensionApiVersions
import com.m3u.extension.api.ExtensionErrorCode
import com.m3u.extension.api.ExtensionErrorCodes
import com.m3u.extension.api.ExtensionResult
import com.m3u.extension.api.HostHookSpecs
import com.m3u.extension.api.HookResult
import com.m3u.extension.api.SerializedExtensionEnvelope
import com.m3u.extension.api.security.BrokerProtocolVersions
import com.m3u.extension.api.security.BrokerScopeHandle
import com.m3u.extension.runtime.ExtensionBrokerScopeLease
import com.m3u.extension.runtime.ExtensionBrokerScopeProvider
import com.m3u.extension.runtime.ExtensionBrokerScopeRequest
import com.m3u.extension.runtime.ExtensionRegistrationResult
import com.m3u.extension.runtime.ExtensionRuntime
import com.m3u.extension.runtime.ExtensionTransportHealth
import com.m3u.extension.runtime.InvocationPolicy
import com.m3u.extension.transport.android.AndroidBoundExtensionTransport
import com.m3u.extension.transport.android.AndroidExtensionDiscovery
import com.m3u.extension.transport.android.InstalledExtensionService
import com.m3u.extension.transport.android.ipc.IExtensionHostBridge
import com.m3u.extension.transport.android.ipc.IExtensionResultCallback
import com.m3u.testing.hostile.HostileExtensionFixtureProtocol
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HostileExternalExtensionIpcTest {
    @Test
    fun hostileExtensionFailuresStayOutsideTheHostAndEveryConnectionRecovers() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val installed = AndroidExtensionDiscovery(context).discover().singleOrNull { candidate ->
            candidate.serviceName == HostileExtensionFixtureProtocol.SERVICE_CLASS_NAME
        } ?: error("Hostile extension fixture was not discovered in the test APK")

        assertEquals(TEST_APK_PACKAGE, installed.packageName)
        assertNull(installed.incompatibilityReason)
        assertNotEquals(Process.myUid(), installed.uid)
        assertEquals(context.applicationInfo.uid, Process.myUid())

        exerciseValidAndInvalidResponses(context, installed)
        exerciseIgnoredCancellationAndLateResponse(context, installed)
        exerciseProcessDeathAndReconnect(context, installed)
    }

    private suspend fun exerciseValidAndInvalidResponses(
        context: Context,
        installed: InstalledExtensionService,
    ) {
        val harness = connect(
            context = context,
            installed = installed,
            timeoutMillis = STANDARD_INVOCATION_TIMEOUT_MILLIS,
        )
        try {
            val baseline = harness.invoke(HostileExtensionFixtureProtocol.BASELINE_TASK_ID)
                .requireSuccess()
            assertExtensionIdentity(baseline, installed)

            val retained = harness.invoke(HostileExtensionFixtureProtocol.RETAIN_BRIDGE_TASK_ID)
                .requireSuccess()
            assertEquals(
                "true",
                retained.output[HostileExtensionFixtureProtocol.OUTPUT_BRIDGE_RETAINED],
            )
            assertEquals(
                "true",
                retained.output[
                    HostileExtensionFixtureProtocol.OUTPUT_ACTIVE_BRIDGE_CALLBACK
                ],
            )
            assertEquals(
                ACTIVE_BRIDGE_CALLBACK_CODE,
                retained.output[
                    HostileExtensionFixtureProtocol.OUTPUT_ACTIVE_BRIDGE_CALLBACK_CODE
                ],
            )

            val retainedProbe = harness
                .invoke(HostileExtensionFixtureProtocol.USE_RETAINED_BRIDGE_TASK_ID)
                .requireSuccess()
            assertEquals(
                "true",
                retainedProbe.output[
                    HostileExtensionFixtureProtocol.OUTPUT_RETAINED_BRIDGE_PRESENT
                ],
            )
            assertEquals(
                "false",
                retainedProbe.output[
                    HostileExtensionFixtureProtocol.OUTPUT_RETAINED_BRIDGE_CALLBACK
                ],
            )
            assertEquals(
                "",
                retainedProbe.output[
                    HostileExtensionFixtureProtocol.OUTPUT_RETAINED_BRIDGE_CALLBACK_CODE
                ],
            )

            repeat(MALFORMED_RESPONSE_REPETITIONS) {
                harness.invoke(HostileExtensionFixtureProtocol.MALFORMED_TASK_ID)
                    .requireFailure(ExtensionErrorCodes.InvocationFailed)
            }
            assertTrue(harness.transport.isConnectionAvailable)

            harness.invoke(HostileExtensionFixtureProtocol.OVERSIZE_VALID_TASK_ID)
                .requireFailure(ExtensionErrorCodes.InvocationFailed)
            assertTrue(harness.transport.isConnectionAvailable)

            val afterInvalidResponses =
                harness.invoke(HostileExtensionFixtureProtocol.BASELINE_TASK_ID).requireSuccess()
            assertExtensionIdentity(afterInvalidResponses, installed)
        } finally {
            harness.close()
        }
    }

    private suspend fun exerciseIgnoredCancellationAndLateResponse(
        context: Context,
        installed: InstalledExtensionService,
    ) {
        val harness = connect(
            context = context,
            installed = installed,
            timeoutMillis = HOST_TIMEOUT_MILLIS,
        )
        try {
            val startedAt = SystemClock.elapsedRealtime()
            harness.invoke(HostileExtensionFixtureProtocol.IGNORE_CANCEL_TASK_ID)
                .requireFailure(ExtensionErrorCodes.InvocationTimedOut)
            val elapsed = SystemClock.elapsedRealtime() - startedAt
            assertTrue(
                "The host must return before the uncooperative extension's delayed callback",
                elapsed < HostileExtensionFixtureProtocol.IGNORE_CANCEL_DELAY_MILLIS,
            )

            delay(
                HostileExtensionFixtureProtocol.IGNORE_CANCEL_DELAY_MILLIS +
                    LATE_CALLBACK_SETTLE_MILLIS
            )
            harness.assertAllInvocationResourcesReleased()
            assertTrue(harness.transport.isConnectionAvailable)

            val afterLateCallback =
                harness.invoke(HostileExtensionFixtureProtocol.BASELINE_TASK_ID).requireSuccess()
            assertExtensionIdentity(afterLateCallback, installed)
        } finally {
            harness.close()
        }
    }

    private suspend fun exerciseProcessDeathAndReconnect(
        context: Context,
        installed: InstalledExtensionService,
    ) {
        val beforeDeath = connect(
            context = context,
            installed = installed,
            timeoutMillis = STANDARD_INVOCATION_TIMEOUT_MILLIS,
        )
        val killedPid: Int
        try {
            val baseline =
                beforeDeath.invoke(HostileExtensionFixtureProtocol.BASELINE_TASK_ID).requireSuccess()
            assertExtensionIdentity(baseline, installed)
            killedPid = checkNotNull(
                baseline.output[HostileExtensionFixtureProtocol.OUTPUT_EXTENSION_PID]
            ).toInt()

            beforeDeath.invoke(HostileExtensionFixtureProtocol.PROCESS_DEATH_TASK_ID)
                .requireAnyFailure(
                    setOf(
                        ExtensionErrorCodes.InvocationFailed,
                        ExtensionErrorCodes.InvocationTimedOut,
                    )
                )
            awaitCondition("dead extension connection was still reported as available") {
                !beforeDeath.transport.isConnectionAvailable
            }
        } finally {
            beforeDeath.close()
        }

        val afterDeath = connectWithRetry(
            context = context,
            installed = installed,
            timeoutMillis = STANDARD_INVOCATION_TIMEOUT_MILLIS,
        )
        try {
            val baseline =
                afterDeath.invoke(HostileExtensionFixtureProtocol.BASELINE_TASK_ID).requireSuccess()
            assertExtensionIdentity(baseline, installed)
            val restartedPid = checkNotNull(
                baseline.output[HostileExtensionFixtureProtocol.OUTPUT_EXTENSION_PID]
            ).toInt()
            assertNotEquals(killedPid, restartedPid)
        } finally {
            afterDeath.close()
        }
    }

    private suspend fun connectWithRetry(
        context: Context,
        installed: InstalledExtensionService,
        timeoutMillis: Long,
    ): Harness {
        var lastFailure: Throwable? = null
        repeat(RECONNECT_ATTEMPTS) {
            try {
                return connect(context, installed, timeoutMillis)
            } catch (failure: Exception) {
                lastFailure = failure
                delay(RECONNECT_RETRY_MILLIS)
            }
        }
        throw AssertionError("Hostile extension did not reconnect", lastFailure)
    }

    private suspend fun connect(
        context: Context,
        installed: InstalledExtensionService,
        timeoutMillis: Long,
    ): Harness {
        val resources = InvocationResourceTracker()
        val transport = AndroidBoundExtensionTransport.connect(
            context = context,
            installed = installed,
            hostBridgeFactory = { manifest, envelope, brokerProtocolVersion ->
                check(manifest.id == HostileExtensionFixtureProtocol.EXTENSION_ID)
                check(brokerProtocolVersion == BrokerProtocolVersions.Current)
                resources.createBridge(envelope)
            },
        )
        try {
            assertEquals(
                HostileExtensionFixtureProtocol.EXTENSION_ID,
                transport.manifest.id,
            )
            assertEquals(ExtensionTransportHealth.HEALTHY, transport.health())
            val runtime = ExtensionRuntime(
                hostApiVersion = ExtensionApiVersions.Current,
                brokerScopeProvider = resources,
                invocationPolicy = InvocationPolicy(
                    timeoutMillis = timeoutMillis,
                    maxPayloadBytes =
                        HostileExtensionFixtureProtocol.HOST_RESPONSE_LIMIT_BYTES,
                    unhealthyFailureThreshold = HOSTILE_FAILURE_THRESHOLD,
                ),
            )
            val registration = runtime.register(transport)
            assertTrue(registration is ExtensionRegistrationResult.Registered)
            registration as ExtensionRegistrationResult.Registered
            val registrationToken = checkNotNull(registration.registrationToken)
            val healthy = runtime.recordTransportHealth(
                extensionId = transport.manifest.id,
                registrationToken = registrationToken,
                health = ExtensionTransportHealth.HEALTHY,
            )
            checkNotNull(healthy)
            return Harness(transport, runtime, resources)
        } catch (failure: Throwable) {
            transport.close()
            throw failure
        }
    }

    private fun assertExtensionIdentity(
        result: BackgroundTaskResult,
        installed: InstalledExtensionService,
    ) {
        val extensionPid = checkNotNull(
            result.output[HostileExtensionFixtureProtocol.OUTPUT_EXTENSION_PID]
        ).toInt()
        val extensionUid = checkNotNull(
            result.output[HostileExtensionFixtureProtocol.OUTPUT_EXTENSION_UID]
        ).toInt()
        assertNotEquals(Process.myPid(), extensionPid)
        assertNotEquals(Process.myUid(), extensionUid)
        assertEquals(installed.uid, extensionUid)
        assertEquals(
            "true",
            result.output[HostileExtensionFixtureProtocol.OUTPUT_BROKER_SCOPE_PRESENT],
        )
    }

    private suspend fun awaitCondition(
        failureMessage: String,
        condition: () -> Boolean,
    ) {
        withTimeout(ASYNC_STATE_TIMEOUT_MILLIS) {
            while (!condition()) delay(ASYNC_STATE_POLL_MILLIS)
        }
        assertTrue(failureMessage, condition())
    }

    private class Harness(
        val transport: AndroidBoundExtensionTransport,
        private val runtime: ExtensionRuntime,
        private val resources: InvocationResourceTracker,
    ) : Closeable {
        suspend fun invoke(taskId: String): ExtensionResult<BackgroundTaskResult> {
            val expectedInvocationCount = resources.openedScopeCount + 1
            val result = runtime.invoke(
                extensionId = HostileExtensionFixtureProtocol.EXTENSION_ID,
                spec = HostHookSpecs.BackgroundTask,
                request = BackgroundTaskRequest(taskId),
            )
            resources.awaitReleased(expectedInvocationCount)
            return result
        }

        suspend fun assertAllInvocationResourcesReleased() {
            resources.awaitReleased(resources.openedScopeCount)
        }

        override fun close() {
            transport.close()
            resources.assertBalanced()
        }
    }

    private class InvocationResourceTracker : ExtensionBrokerScopeProvider {
        private val openedScopes = AtomicInteger()
        private val closedScopes = AtomicInteger()
        private val createdBridges = AtomicInteger()
        private val closedBridges = AtomicInteger()
        private val bridgesWithScope = AtomicInteger()

        val openedScopeCount: Int
            get() = openedScopes.get()

        override suspend fun open(
            request: ExtensionBrokerScopeRequest,
        ): ExtensionBrokerScopeLease {
            check(request.manifest.id == HostileExtensionFixtureProtocol.EXTENSION_ID)
            check(request.hook == HostHookSpecs.BackgroundTask.hook)
            val ordinal = openedScopes.incrementAndGet()
            return object : ExtensionBrokerScopeLease {
                private val closed = AtomicBoolean(false)

                override val handle = BrokerScopeHandle("hostile-test-scope-$ordinal")

                override fun close() {
                    if (closed.compareAndSet(false, true)) closedScopes.incrementAndGet()
                }
            }
        }

        fun createBridge(envelope: SerializedExtensionEnvelope): IExtensionHostBridge {
            createdBridges.incrementAndGet()
            if (envelope.brokerScope != null) bridgesWithScope.incrementAndGet()
            return TrackingHostBridge {
                closedBridges.incrementAndGet()
            }
        }

        suspend fun awaitReleased(expectedInvocationCount: Int) {
            withTimeout(ASYNC_STATE_TIMEOUT_MILLIS) {
                while (
                    openedScopes.get() != expectedInvocationCount ||
                    closedScopes.get() != expectedInvocationCount ||
                    createdBridges.get() != expectedInvocationCount ||
                    closedBridges.get() != expectedInvocationCount
                ) {
                    delay(ASYNC_STATE_POLL_MILLIS)
                }
            }
            assertEquals(expectedInvocationCount, bridgesWithScope.get())
        }

        fun assertBalanced() {
            assertEquals(openedScopes.get(), closedScopes.get())
            assertEquals(createdBridges.get(), closedBridges.get())
            assertEquals(createdBridges.get(), bridgesWithScope.get())
        }
    }

    private class TrackingHostBridge(
        private val onClose: () -> Unit,
    ) : IExtensionHostBridge.Stub(), Closeable {
        private val closed = AtomicBoolean(false)

        override fun executeHttp(
            requestId: String,
            request: ParcelFileDescriptor,
            callback: IExtensionResultCallback,
        ) {
            runCatching { request.close() }
            runCatching {
                callback.onFailure(
                    requestId,
                    ACTIVE_BRIDGE_CALLBACK_CODE,
                    "Hostile fixture unexpectedly reached a current host bridge",
                )
            }
        }

        override fun cancelHttp(requestId: String?) = Unit

        override fun close() {
            if (closed.compareAndSet(false, true)) onClose()
        }
    }

    private fun ExtensionResult<BackgroundTaskResult>.requireSuccess(): BackgroundTaskResult =
        when (val result = outcome) {
            is HookResult.Success -> result.payload
            is HookResult.Failure -> error(
                "Expected extension success, received ${result.error.code}: " +
                    result.error.message
            )
        }

    private fun ExtensionResult<BackgroundTaskResult>.requireFailure(
        expectedCode: ExtensionErrorCode,
    ) {
        val failure = outcome as? HookResult.Failure
            ?: error("Expected extension failure, received success")
        assertEquals(expectedCode, failure.error.code)
    }

    private fun ExtensionResult<BackgroundTaskResult>.requireAnyFailure(
        expectedCodes: Set<ExtensionErrorCode>,
    ) {
        val failure = outcome as? HookResult.Failure
            ?: error("Expected extension failure, received success")
        assertTrue(
            "Unexpected extension error code ${failure.error.code}",
            failure.error.code in expectedCodes,
        )
    }

    private companion object {
        const val TEST_APK_PACKAGE = "com.m3u.smartphone.test"
        const val STANDARD_INVOCATION_TIMEOUT_MILLIS = 10_000L
        const val HOST_TIMEOUT_MILLIS = 350L
        const val LATE_CALLBACK_SETTLE_MILLIS = 250L
        const val ASYNC_STATE_TIMEOUT_MILLIS = 5_000L
        const val ASYNC_STATE_POLL_MILLIS = 20L
        const val RECONNECT_ATTEMPTS = 20
        const val RECONNECT_RETRY_MILLIS = 100L
        const val MALFORMED_RESPONSE_REPETITIONS = 17
        const val HOSTILE_FAILURE_THRESHOLD = 100
        const val ACTIVE_BRIDGE_CALLBACK_CODE = "test.active_bridge_probe"
    }
}
