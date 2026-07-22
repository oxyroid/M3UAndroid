package com.m3u.data.extension.security

import android.content.Context
import android.os.ParcelFileDescriptor
import com.m3u.extension.api.security.BrokerInvocation
import com.m3u.extension.api.security.HostNetworkBroker
import com.m3u.extension.transport.android.ParcelFileCodec
import com.m3u.extension.transport.android.ipc.IExtensionHostBridge
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal class ExtensionHostBridge(
    private val context: Context,
    private val broker: HostNetworkBroker,
    private val extensionId: String,
) : IExtensionHostBridge.Stub() {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    override fun executeHttp(request: ParcelFileDescriptor): ParcelFileDescriptor = runBlocking {
        val invocation = json.decodeFromString<BrokerInvocation>(
            ParcelFileCodec.read(request, MAX_REQUEST_BYTES)
        )
        val response = broker.execute(
            extensionId = extensionId,
            accountId = invocation.accountId,
            request = invocation.request,
        )
        ParcelFileCodec.write(context, json.encodeToString(response))
    }

    private companion object {
        const val MAX_REQUEST_BYTES = 2 * 1024 * 1024
    }
}
