package com.m3u.extension.runtime

import com.m3u.extension.api.EmptyExtensionPayload
import com.m3u.extension.api.ExtensionApiRange
import com.m3u.extension.api.ExtensionApiVersion
import com.m3u.extension.api.ExtensionApiVersions
import com.m3u.extension.api.ExtensionCapabilityIds
import com.m3u.extension.api.ExtensionCapabilityRequest
import com.m3u.extension.api.ExtensionEntrypoint
import com.m3u.extension.api.ExtensionErrorCodes
import com.m3u.extension.api.ExtensionHook
import com.m3u.extension.api.ExtensionHookDeclaration
import com.m3u.extension.api.ExtensionHookIds
import com.m3u.extension.api.ExtensionHookOutcome
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.ExtensionInvocation
import com.m3u.extension.api.ExtensionManifest
import com.m3u.extension.api.ExtensionPayload
import com.m3u.extension.api.Hook
import com.m3u.extension.api.InvocationId
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ExtensionRuntimeTest {
    @Test
    fun `runtime registers queries and invokes declared hook`() {
        val runtime = runtime()
        val entrypoint = entrypoint()

        assertIs<ExtensionRegistrationResult.Registered>(runtime.register(entrypoint))
        assertEquals(entrypoint.manifest.id, runtime.extensionsSupporting(PLAYBACK_HOOK).single().manifest.id)

        val result = runSuspend {
            runtime.invoke(
                extensionId = entrypoint.manifest.id,
                hook = PLAYBACK_HOOK,
                grantedCapabilities = setOf(ExtensionCapabilityIds.PlaybackResolve),
                payload = TestPayload("stable-reference"),
            )
        }

        assertEquals(InvocationId("invocation-1"), result.invocationId)
        val success = assertIs<ExtensionHookOutcome.Success>(result.outcome)
        assertEquals(TestPayload("resolved-stable-reference"), success.payload)
    }

    @Test
    fun `runtime rejects invocation without required capability`() {
        val runtime = runtime()
        val entrypoint = entrypoint()
        runtime.register(entrypoint)

        val result = runSuspend {
            runtime.invoke(
                extensionId = entrypoint.manifest.id,
                hook = PLAYBACK_HOOK,
                grantedCapabilities = emptySet(),
                payload = EmptyExtensionPayload,
            )
        }

        val failure = assertIs<ExtensionHookOutcome.Failure>(result.outcome)
        assertEquals(ExtensionErrorCodes.CapabilityDenied, failure.error.code)
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
    fun `runtime converts provider exception into structured failure`() {
        val runtime = runtime()
        val entrypoint = entrypoint(
            hook = object : ExtensionHook {
                override val hook: Hook = PLAYBACK_HOOK

                override suspend fun invoke(invocation: ExtensionInvocation): ExtensionHookOutcome {
                    error("Provider unavailable")
                }
            }
        )
        runtime.register(entrypoint)

        val result = runSuspend {
            runtime.invoke(
                extensionId = entrypoint.manifest.id,
                hook = PLAYBACK_HOOK,
                grantedCapabilities = setOf(ExtensionCapabilityIds.PlaybackResolve),
                payload = EmptyExtensionPayload,
            )
        }

        val failure = assertIs<ExtensionHookOutcome.Failure>(result.outcome)
        assertEquals(ExtensionErrorCodes.InvocationFailed, failure.error.code)
        assertEquals("IllegalStateException", failure.error.details["exception"])
    }

    private fun runtime(): ExtensionRuntime = ExtensionRuntime(
        hostApiVersion = ExtensionApiVersions.Current,
        invocationIdFactory = InvocationIdFactory { InvocationId("invocation-1") },
    )

    private fun entrypoint(
        apiRange: ExtensionApiRange = ExtensionApiRange(
            minimum = ExtensionApiVersions.Current,
            maximum = ExtensionApiVersions.Current,
        ),
        hook: ExtensionHook = object : ExtensionHook {
            override val hook: Hook = PLAYBACK_HOOK

            override suspend fun invoke(invocation: ExtensionInvocation): ExtensionHookOutcome {
                val payload = invocation.payload as TestPayload
                return ExtensionHookOutcome.Success(TestPayload("resolved-${payload.value}"))
            }
        },
    ): ExtensionEntrypoint = object : ExtensionEntrypoint {
        override val manifest = ExtensionManifest(
            id = ExtensionId("com.example.provider"),
            displayName = "Example Provider",
            extensionVersion = "1.0.0",
            apiRange = apiRange,
            hooks = setOf(
                ExtensionHookDeclaration(
                    hook = PLAYBACK_HOOK,
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
        override val hooks: Collection<ExtensionHook> = listOf(hook)
    }

    private fun <T> runSuspend(block: suspend () -> T): T {
        var result: Result<T>? = null
        block.startCoroutine(
            object : Continuation<T> {
                override val context = EmptyCoroutineContext

                override fun resumeWith(resumeResult: Result<T>) {
                    result = resumeResult
                }
            }
        )
        return checkNotNull(result).getOrThrow()
    }

    private data class TestPayload(val value: String) : ExtensionPayload

    private companion object {
        val PLAYBACK_HOOK = ExtensionHookIds.PlaybackSourceResolve
    }
}
