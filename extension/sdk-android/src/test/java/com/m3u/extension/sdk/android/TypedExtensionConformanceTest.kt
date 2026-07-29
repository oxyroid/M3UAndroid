package com.m3u.extension.sdk.android

import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.ExtensionManifest
import com.m3u.extension.api.HookResult
import com.m3u.extension.api.HostHookSpecs
import com.m3u.extension.api.InvocationId
import com.m3u.extension.api.SerializedExtensionEnvelope
import com.m3u.extension.api.SerializedExtensionResult
import com.m3u.extension.conformance.ExtensionConformanceDriver
import com.m3u.extension.conformance.ExtensionConformanceFixtureState
import com.m3u.extension.conformance.ExtensionConformanceFixtures
import com.m3u.extension.conformance.ExtensionConformanceSuite
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Test

class TypedExtensionConformanceTest {
    @Test
    fun typedSdkBackendPassesTheSharedConformanceSuite() = runBlocking {
        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
        val manifest = ExtensionConformanceFixtures.manifest(
            id = ExtensionId("com.m3u.testing.conformance.sdk"),
            displayName = "SDK conformance fixture",
        )
        val fixtureState = ExtensionConformanceFixtureState()
        val registry = TypedHookRegistry().apply {
            handle(HostHookSpecs.BackgroundTask) { request, context ->
                HookResult.Success(fixtureState.handle(request, context))
            }
        }
        val backend = registry.createBackend(manifest, json)
        val driver = object : ExtensionConformanceDriver {
            override val manifest: ExtensionManifest = manifest

            override suspend fun invoke(
                envelope: SerializedExtensionEnvelope,
            ): SerializedExtensionResult = backend.invoke(envelope)

            override suspend fun cancel(invocationId: InvocationId) {
                backend.cancel(invocationId)
            }
        }

        ExtensionConformanceSuite(json).verifyStandardBehavior(driver)
    }
}
