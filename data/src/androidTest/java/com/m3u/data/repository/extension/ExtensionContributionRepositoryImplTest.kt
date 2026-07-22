package com.m3u.data.repository.extension

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.m3u.extension.api.EpgRefreshRequest
import com.m3u.extension.api.EpgRefreshResult
import com.m3u.extension.api.ExtensionApiRange
import com.m3u.extension.api.ExtensionApiVersions
import com.m3u.extension.api.ExtensionCallContext
import com.m3u.extension.api.ExtensionCapabilityIds
import com.m3u.extension.api.ExtensionCapabilityRequest
import com.m3u.extension.api.ExtensionEntrypoint
import com.m3u.extension.api.ExtensionError
import com.m3u.extension.api.ExtensionErrorCodes
import com.m3u.extension.api.ExtensionHandler
import com.m3u.extension.api.ExtensionHookDeclaration
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.ExtensionManifest
import com.m3u.extension.api.ExtensionProgramme
import com.m3u.extension.api.ExtensionSemanticVersion
import com.m3u.extension.api.HookResult
import com.m3u.extension.api.HostHookSpecs
import com.m3u.extension.runtime.ExtensionRegistrationResult
import com.m3u.extension.runtime.ExtensionRuntime
import com.m3u.extension.runtime.InvocationPolicy
import java.util.concurrent.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExtensionContributionRepositoryImplTest {
    @Test
    fun epgHookFailuresAndExceptionsDoNotProduceSuccessfulBatches() = runBlocking {
        val runtime = runtimeWith(
            epgExtension(FAILURE_EXTENSION_ID) {
                HookResult.Failure(
                    ExtensionError(
                        code = ExtensionErrorCodes.InvocationFailed,
                        message = "Expected test failure",
                        recoverable = true,
                    )
                )
            },
            epgExtension(EXCEPTION_EXTENSION_ID) {
                throw IllegalStateException("Expected test exception")
            },
        )

        val contributions = ExtensionContributionRepositoryImpl(runtime).refreshEpg(
            channelReferences = listOf(CHANNEL_REFERENCE),
            fromEpochMillis = WINDOW_START,
            toEpochMillis = WINDOW_END,
        )

        assertTrue(contributions.isEmpty())
    }

    @Test
    fun successfulEpgHookWithNoProgrammesKeepsItsExtensionBatch() = runBlocking {
        val runtime = runtimeWith(
            epgExtension(EMPTY_SUCCESS_EXTENSION_ID) {
                HookResult.Success(EpgRefreshResult(programmes = emptyList()))
            }
        )

        val contributions = ExtensionContributionRepositoryImpl(runtime).refreshEpg(
            channelReferences = listOf(CHANNEL_REFERENCE),
            fromEpochMillis = WINDOW_START,
            toEpochMillis = WINDOW_END,
        )

        assertEquals(1, contributions.size)
        assertEquals(EMPTY_SUCCESS_EXTENSION_ID, contributions.single().extensionId)
        assertTrue(contributions.single().programmes.isEmpty())
    }

    @Test
    fun epgHookCancellationPropagatesToTheCaller() {
        val runtime = runtimeWith(
            epgExtension(CANCELLED_EXTENSION_ID) {
                throw CancellationException(CANCELLATION_MESSAGE)
            }
        )

        val thrown = assertThrows(CancellationException::class.java) {
            runBlocking {
                ExtensionContributionRepositoryImpl(runtime).refreshEpg(
                    channelReferences = listOf(CHANNEL_REFERENCE),
                    fromEpochMillis = WINDOW_START,
                    toEpochMillis = WINDOW_END,
                )
            }
        }

        assertEquals(CANCELLATION_MESSAGE, thrown.message)
    }

    @Test
    fun tooManyChannelReferencesDoNotInvokeExtensionsOrProduceSuccessfulBatches() = runBlocking {
        var invocationCount = 0
        val runtime = runtimeWith(
            epgExtension(CHANNEL_LIMIT_EXTENSION_ID) {
                invocationCount += 1
                HookResult.Success(EpgRefreshResult(programmes = emptyList()))
            }
        )

        val contributions = ExtensionContributionRepositoryImpl(runtime).refreshEpg(
            channelReferences = List(MAX_CHANNELS_PER_ENRICHMENT + 1) { index ->
                "channel-$index"
            },
            fromEpochMillis = WINDOW_START,
            toEpochMillis = WINDOW_END,
        )

        assertTrue(contributions.isEmpty())
        assertEquals(0, invocationCount)
    }

    @Test
    fun oversizedEpgBatchDoesNotProduceSuccessfulExtensionBatch() = runBlocking {
        var invocationCount = 0
        val runtime = runtimeWith(
            epgExtension(BATCH_LIMIT_EXTENSION_ID) { request ->
                invocationCount += 1
                HookResult.Success(
                    EpgRefreshResult(
                        programmes = List(MAX_PROGRAMMES_PER_BATCH + 1) {
                            validProgramme(request.sourceIds.single())
                        },
                    )
                )
            },
            invocationPolicy = LARGE_PAYLOAD_INVOCATION_POLICY,
        )

        val contributions = ExtensionContributionRepositoryImpl(runtime).refreshEpg(
            channelReferences = listOf(CHANNEL_REFERENCE),
            fromEpochMillis = WINDOW_START,
            toEpochMillis = WINDOW_END,
        )

        assertTrue(contributions.isEmpty())
        assertEquals(1, invocationCount)
    }

    @Test
    fun cumulativeEpgProgrammeLimitDoesNotProduceSuccessfulExtensionBatch() = runBlocking {
        var invocationCount = 0
        val runtime = runtimeWith(
            epgExtension(EXTENSION_LIMIT_EXTENSION_ID) { request ->
                invocationCount += 1
                val programmeCount = if (invocationCount <= FULL_EPG_BATCH_COUNT) {
                    MAX_PROGRAMMES_PER_BATCH
                } else {
                    1
                }
                HookResult.Success(
                    EpgRefreshResult(
                        programmes = List(programmeCount) {
                            validProgramme(request.sourceIds.first())
                        },
                    )
                )
            },
            invocationPolicy = LARGE_PAYLOAD_INVOCATION_POLICY,
        )

        val contributions = ExtensionContributionRepositoryImpl(runtime).refreshEpg(
            channelReferences = List(CHANNEL_ENRICHMENT_BATCH_SIZE * FULL_EPG_BATCH_COUNT + 1) {
                index -> "channel-$index"
            },
            fromEpochMillis = WINDOW_START,
            toEpochMillis = WINDOW_END,
        )

        assertTrue(contributions.isEmpty())
        assertEquals(FULL_EPG_BATCH_COUNT + 1, invocationCount)
    }

    private fun runtimeWith(
        vararg entrypoints: ExtensionEntrypoint,
        invocationPolicy: InvocationPolicy = InvocationPolicy(),
    ): ExtensionRuntime =
        ExtensionRuntime(
            hostApiVersion = ExtensionApiVersions.Current,
            invocationPolicy = invocationPolicy,
        ).also { runtime ->
            entrypoints.forEach { entrypoint ->
                assertTrue(runtime.register(entrypoint) is ExtensionRegistrationResult.Registered)
            }
        }

    private fun validProgramme(channelReference: String) = ExtensionProgramme(
        channelReference = channelReference,
        title = "Programme",
        startEpochMillis = WINDOW_START,
        endEpochMillis = WINDOW_END,
    )

    private fun epgExtension(
        extensionId: ExtensionId,
        invoke: suspend (EpgRefreshRequest) -> HookResult<EpgRefreshResult>,
    ): ExtensionEntrypoint = object : ExtensionEntrypoint {
        override val manifest = ExtensionManifest(
            id = extensionId,
            displayName = "EPG repository test",
            extensionVersion = ExtensionSemanticVersion(1, 0, 0),
            apiRange = ExtensionApiRange(
                minimum = ExtensionApiVersions.Current,
                maximum = ExtensionApiVersions.Current,
            ),
            hooks = setOf(
                ExtensionHookDeclaration(
                    hook = HostHookSpecs.EpgRefresh.hook,
                    schemaVersion = HostHookSpecs.EpgRefresh.schemaVersion,
                    requiredCapabilities = setOf(ExtensionCapabilityIds.EpgRead),
                )
            ),
            capabilities = setOf(
                ExtensionCapabilityRequest(
                    capability = ExtensionCapabilityIds.EpgRead,
                    reason = "Contribute test EPG data",
                )
            ),
        )
        override val handlers: Collection<ExtensionHandler<*, *>> = listOf(
            object : ExtensionHandler<EpgRefreshRequest, EpgRefreshResult> {
                override val spec = HostHookSpecs.EpgRefresh

                override suspend fun invoke(
                    context: ExtensionCallContext,
                    request: EpgRefreshRequest,
                ): HookResult<EpgRefreshResult> = invoke(request)
            }
        )
    }

    private companion object {
        val FAILURE_EXTENSION_ID = ExtensionId("com.m3u.test.epg.failure")
        val EXCEPTION_EXTENSION_ID = ExtensionId("com.m3u.test.epg.exception")
        val EMPTY_SUCCESS_EXTENSION_ID = ExtensionId("com.m3u.test.epg.empty")
        val CANCELLED_EXTENSION_ID = ExtensionId("com.m3u.test.epg.cancelled")
        val CHANNEL_LIMIT_EXTENSION_ID = ExtensionId("com.m3u.test.epg.channel-limit")
        val BATCH_LIMIT_EXTENSION_ID = ExtensionId("com.m3u.test.epg.batch-limit")
        val EXTENSION_LIMIT_EXTENSION_ID = ExtensionId("com.m3u.test.epg.extension-limit")
        val LARGE_PAYLOAD_INVOCATION_POLICY = InvocationPolicy(maxPayloadBytes = 16 * 1_048_576)
        const val CHANNEL_REFERENCE = "channel-1"
        const val WINDOW_START = 1_000L
        const val WINDOW_END = 2_000L
        const val CANCELLATION_MESSAGE = "Expected test cancellation"
        const val CHANNEL_ENRICHMENT_BATCH_SIZE = 200
        const val MAX_CHANNELS_PER_ENRICHMENT = 5_000
        const val MAX_PROGRAMMES_PER_BATCH = 10_000
        const val MAX_PROGRAMMES_PER_EXTENSION = 50_000
        const val FULL_EPG_BATCH_COUNT = MAX_PROGRAMMES_PER_EXTENSION / MAX_PROGRAMMES_PER_BATCH
    }
}
