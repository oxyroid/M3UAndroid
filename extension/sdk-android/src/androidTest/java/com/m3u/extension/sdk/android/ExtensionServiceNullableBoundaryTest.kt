package com.m3u.extension.sdk.android

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.m3u.extension.api.ExtensionApiRange
import com.m3u.extension.api.ExtensionApiVersions
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.ExtensionManifest
import com.m3u.extension.api.ExtensionSemanticVersion
import com.m3u.extension.api.InvocationId
import com.m3u.extension.api.SerializedExtensionEnvelope
import com.m3u.extension.api.SerializedExtensionResult
import com.m3u.extension.transport.android.ExtensionProtocol
import com.m3u.extension.transport.android.ParcelFileCodec
import com.m3u.extension.transport.android.ipc.IExtensionService
import java.io.IOException
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExtensionServiceNullableBoundaryTest {
    @Test
    fun invalidNullableHandshakeClosesTheReceivedDescriptor() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val service = NullableBoundaryExtensionService()
        val binder = IExtensionService.Stub.asInterface(
            service.onBind(Intent(ExtensionProtocol.SERVICE_ACTION))
        )
        val request = ParcelFileCodec.write(context, "{}")

        binder.handshake(null, request, null)

        assertThrows(IOException::class.java) {
            ParcelFileCodec.read(request, 16)
        }
        service.onDestroy()
    }

    private class NullableBoundaryExtensionService : ExtensionService() {
        override val serviceBackend: ExtensionServiceBackend =
            object : ExtensionServiceBackend {
                override val manifest = ExtensionManifest(
                    id = ExtensionId("com.example.nullable-boundary"),
                    displayName = "Nullable boundary test",
                    extensionVersion = ExtensionSemanticVersion(1, 0, 0),
                    apiRange = ExtensionApiRange(
                        minimum = ExtensionApiVersions.Current,
                        maximum = ExtensionApiVersions.Current,
                    ),
                    hooks = emptySet(),
                    capabilities = emptySet(),
                )

                override suspend fun invoke(
                    envelope: SerializedExtensionEnvelope,
                    hostNetworkBroker: ExtensionHostNetworkBroker?,
                ): SerializedExtensionResult = error("Not used")

                override suspend fun cancel(invocationId: InvocationId) = Unit

                override suspend fun healthWireValue(): String = "healthy"
            }
    }
}
