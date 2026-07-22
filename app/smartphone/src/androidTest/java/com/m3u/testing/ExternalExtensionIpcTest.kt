package com.m3u.testing

import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import com.m3u.extension.api.ExtensionApiVersions
import com.m3u.extension.api.HookResult
import com.m3u.extension.api.BackgroundTaskRequest
import com.m3u.extension.api.HostHookSpecs
import com.m3u.extension.api.SearchProviderRequest
import com.m3u.extension.api.SearchProviderResult
import com.m3u.extension.api.subscription.SubscriptionHookSpecs
import com.m3u.extension.api.subscription.SubscriptionProviderDiscoverRequest
import com.m3u.extension.runtime.ExtensionRegistrationResult
import com.m3u.extension.runtime.ExtensionRuntime
import com.m3u.extension.runtime.InvocationPolicy
import com.m3u.extension.transport.android.AndroidBoundExtensionTransport
import com.m3u.extension.transport.android.AndroidExtensionDiscovery
import com.m3u.extension.transport.android.ipc.IExtensionHostBridge
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class ExternalExtensionIpcTest {
    @Test
    fun referenceApkIsDiscoveredBoundAndInvokedThroughStreamTransport() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val installed = AndroidExtensionDiscovery(context).discover().firstOrNull {
            it.packageName == REFERENCE_PACKAGE
        }
        assumeTrue("Reference extension APK is not installed", installed != null)
        val transport = AndroidBoundExtensionTransport.connect(
            context = context,
            installed = requireNotNull(installed),
            hostBridgeFactory = { RejectingHostBridge },
        )
        try {
            val runtime = ExtensionRuntime(
                ExtensionApiVersions.Current,
                invocationPolicy = InvocationPolicy(maxPayloadBytes = 2_000_000),
            )
            assertTrue(runtime.register(transport) is ExtensionRegistrationResult.Registered)

            val result = runtime.invoke(
                extensionId = transport.manifest.id,
                spec = SubscriptionHookSpecs.Discover,
                request = SubscriptionProviderDiscoverRequest(),
            )
            val payload = (result.outcome as HookResult.Success<*>).payload as
                com.m3u.extension.api.subscription.SubscriptionProviderDiscoverResult

            assertEquals("Reference Provider", payload.providers.single().displayName)
            assertEquals("reference", payload.providers.single().supportedKinds.single().value)

            val largeResult = runtime.invoke(
                extensionId = transport.manifest.id,
                spec = HostHookSpecs.SearchProvider,
                request = SearchProviderRequest("large"),
            )
            val search = (largeResult.outcome as HookResult.Success<*>).payload as SearchProviderResult
            assertEquals(1_200_000, search.items.single().subtitle?.length)

            val slowInvocation = async {
                runtime.invoke(
                    extensionId = transport.manifest.id,
                    spec = HostHookSpecs.BackgroundTask,
                    request = BackgroundTaskRequest("slow"),
                )
            }
            delay(200)
            slowInvocation.cancel()
            runCatching { slowInvocation.await() }
            delay(200)
            val cancelStatus = runtime.invoke(
                extensionId = transport.manifest.id,
                spec = HostHookSpecs.BackgroundTask,
                request = BackgroundTaskRequest("cancel-status"),
            )
            val status = (cancelStatus.outcome as HookResult.Success<*>).payload as
                com.m3u.extension.api.BackgroundTaskResult
            assertEquals("true", status.output["cancelled"])
        } finally {
            transport.close()
        }
    }

    private object RejectingHostBridge : IExtensionHostBridge.Stub() {
        override fun executeHttp(request: ParcelFileDescriptor): ParcelFileDescriptor =
            error("Reference discovery must not request network access")
    }

    private companion object {
        const val REFERENCE_PACKAGE = "com.m3u.testing.extension.reference"
    }
}
