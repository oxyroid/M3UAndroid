package com.m3u.testing

import android.os.ParcelFileDescriptor
import android.os.Process
import androidx.test.platform.app.InstrumentationRegistry
import com.m3u.extension.api.ExtensionManifest
import com.m3u.extension.api.InvocationId
import com.m3u.extension.api.SerializedExtensionEnvelope
import com.m3u.extension.api.SerializedExtensionResult
import com.m3u.extension.conformance.ExtensionConformanceDriver
import com.m3u.extension.conformance.ExtensionConformanceSuite
import com.m3u.extension.transport.android.AndroidBoundExtensionTransport
import com.m3u.extension.transport.android.AndroidExtensionDiscovery
import com.m3u.extension.transport.android.ipc.IExtensionHostBridge
import com.m3u.extension.transport.android.ipc.IExtensionResultCallback
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExternalExtensionConformanceIpcTest {
    @Test
    fun typedServicePassesSharedSuiteAcrossBinderAndPfd() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation()
            .targetContext
            .applicationContext
        val installed = AndroidExtensionDiscovery(context).discover().singleOrNull { candidate ->
            candidate.packageName == REFERENCE_PACKAGE &&
                candidate.serviceName == REFERENCE_SERVICE
        } ?: error("Reference extension APK was not installed for the conformance suite")

        assertEquals(REFERENCE_PACKAGE, installed.packageName)
        assertNotEquals(Process.myUid(), installed.uid)
        assertNull(installed.incompatibilityReason)

        val transport = AndroidBoundExtensionTransport.connect(
            context = context,
            installed = installed,
            hostBridgeFactory = { _, _, _ -> NoOpHostBridge },
        )
        try {
            val driver = object : ExtensionConformanceDriver {
                override val manifest: ExtensionManifest = transport.manifest

                override suspend fun invoke(
                    envelope: SerializedExtensionEnvelope,
                ): SerializedExtensionResult = transport.invoke(envelope)

                override suspend fun cancel(invocationId: InvocationId) {
                    transport.cancel(invocationId)
                }
            }

            ExtensionConformanceSuite().verifyStandardBehavior(driver)
        } finally {
            transport.close()
        }
    }

    private object NoOpHostBridge : IExtensionHostBridge.Stub() {
        override fun executeHttp(
            requestId: String,
            request: ParcelFileDescriptor,
            callback: IExtensionResultCallback,
        ) {
            runCatching { request.close() }
            runCatching {
                callback.onFailure(
                    requestId,
                    "test.unexpected_broker_call",
                    "The conformance fixture is offline",
                )
            }
        }

        override fun cancelHttp(requestId: String?) = Unit
    }

    private companion object {
        const val REFERENCE_PACKAGE = "com.m3u.testing.extension.reference"
        const val REFERENCE_SERVICE =
            "com.m3u.testing.extension.reference.ReferenceExtensionService"
    }
}
