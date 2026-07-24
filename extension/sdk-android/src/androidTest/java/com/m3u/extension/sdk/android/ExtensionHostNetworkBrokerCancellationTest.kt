package com.m3u.extension.sdk.android

import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.m3u.extension.api.security.BrokerProtocolVersions
import com.m3u.extension.api.security.BrokeredHttpRequest
import com.m3u.extension.transport.android.ipc.IExtensionHostBridge
import com.m3u.extension.transport.android.ipc.IExtensionResultCallback
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExtensionHostNetworkBrokerCancellationTest {
    @Test
    fun cancellingBrokerCallCancelsTheMatchingHostRequest() = runBlocking {
        val sentRequestId = CompletableDeferred<String>()
        val cancelledRequestId = CompletableDeferred<String>()
        val bridge = object : IExtensionHostBridge.Stub() {
            override fun executeHttp(
                requestId: String?,
                request: ParcelFileDescriptor?,
                callback: IExtensionResultCallback?,
            ) {
                request?.close()
                requestId?.let(sentRequestId::complete)
            }

            override fun cancelHttp(requestId: String?) {
                requestId?.let(cancelledRequestId::complete)
            }
        }
        val broker = ExtensionHostNetworkBroker(
            context = ApplicationProvider.getApplicationContext<Context>(),
            bridge = bridge,
            json = Json { ignoreUnknownKeys = true; explicitNulls = false },
            brokerProtocolVersion = BrokerProtocolVersions.Current,
        )
        try {
            val execution = async(Dispatchers.IO) {
                broker.execute(
                    BrokeredHttpRequest(
                        method = "GET",
                        url = "https://media.example.test/items",
                    )
                )
            }
            val sent = withTimeout(5_000) { sentRequestId.await() }

            execution.cancelAndJoin()

            assertEquals(sent, withTimeout(5_000) { cancelledRequestId.await() })
        } finally {
            broker.close()
        }
    }
}
