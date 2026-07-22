package com.m3u.extension.sdk.android

import android.content.Context
import com.m3u.extension.api.security.BrokerInvocation
import com.m3u.extension.api.security.BrokeredHttpRequest
import com.m3u.extension.api.security.BrokeredHttpResponse
import com.m3u.extension.transport.android.ParcelFileCodec
import com.m3u.extension.transport.android.ipc.IExtensionHostBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** A capability-scoped network client. It never exposes provider credentials to the plugin. */
class ExtensionHostNetworkBroker internal constructor(
    private val context: Context,
    private val bridge: IExtensionHostBridge,
    private val json: Json,
) {
    suspend fun execute(
        accountId: String,
        request: BrokeredHttpRequest,
    ): BrokeredHttpResponse = withContext(Dispatchers.IO) {
        val input = ParcelFileCodec.write(
            context,
            json.encodeToString(BrokerInvocation(accountId = accountId, request = request)),
        )
        val output = bridge.executeHttp(input)
        json.decodeFromString(
            ParcelFileCodec.read(output, MAX_RESPONSE_BYTES),
        )
    }

    private companion object {
        const val MAX_RESPONSE_BYTES = 4 * 1024 * 1024
    }
}
