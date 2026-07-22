package com.m3u.extension.sdk.android

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.ParcelFileDescriptor
import com.m3u.extension.api.InvocationId
import com.m3u.extension.api.SerializedExtensionEnvelope
import com.m3u.extension.api.SerializedExtensionResult
import com.m3u.extension.runtime.ExtensionTransport
import com.m3u.extension.transport.android.ExtensionHandshakeRequest
import com.m3u.extension.transport.android.ExtensionHandshakeResponse
import com.m3u.extension.transport.android.ExtensionProtocol
import com.m3u.extension.transport.android.ParcelFileCodec
import com.m3u.extension.transport.android.ipc.IExtensionHostBridge
import com.m3u.extension.transport.android.ipc.IExtensionService
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

abstract class ExtensionService : Service() {
    protected abstract val transport: ExtensionTransport
    protected open val json: Json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    /** Override when a hook needs the host broker. The default delegates to [transport]. */
    protected open suspend fun invoke(
        envelope: SerializedExtensionEnvelope,
        hostNetworkBroker: ExtensionHostNetworkBroker,
    ): SerializedExtensionResult = transport.invoke(envelope)

    private val binder = object : IExtensionService.Stub() {
        override fun handshake(request: ParcelFileDescriptor): ParcelFileDescriptor {
            val handshake = json.decodeFromString<ExtensionHandshakeRequest>(
                ParcelFileCodec.read(request, MAX_HANDSHAKE_BYTES)
            )
            require(handshake.transportVersion == ExtensionProtocol.TRANSPORT_VERSION) {
                "Unsupported extension transport protocol"
            }
            return ParcelFileCodec.write(
                this@ExtensionService,
                json.encodeToString(
                    ExtensionHandshakeResponse(
                        transportVersion = ExtensionProtocol.TRANSPORT_VERSION,
                        extensionApiRange = transport.manifest.apiRange,
                    )
                ),
            )
        }

        override fun openManifest(): ParcelFileDescriptor =
            ParcelFileCodec.write(this@ExtensionService, json.encodeToString(transport.manifest))

        override fun invoke(
            invocationId: String,
            hookId: String,
            schemaVersion: Int,
            request: ParcelFileDescriptor,
            hostBridge: IExtensionHostBridge,
        ): ParcelFileDescriptor = runBlocking {
            val envelope = json.decodeFromString<SerializedExtensionEnvelope>(
                ParcelFileCodec.read(request, MAX_REQUEST_BYTES)
            )
            require(envelope.invocationId.value == invocationId)
            require(envelope.hook.id == hookId)
            require(envelope.schemaVersion == schemaVersion)
            val broker = ExtensionHostNetworkBroker(this@ExtensionService, hostBridge, json)
            ParcelFileCodec.write(
                this@ExtensionService,
                json.encodeToString(invoke(envelope, broker)),
            )
        }

        override fun cancel(invocationId: String) = runBlocking {
            transport.cancel(InvocationId(invocationId))
        }

        override fun health(): String = runBlocking { transport.health().name.lowercase() }
    }

    final override fun onBind(intent: Intent?): IBinder? =
        binder.takeIf { intent?.action == ExtensionProtocol.SERVICE_ACTION }

    private companion object {
        const val MAX_HANDSHAKE_BYTES = 16 * 1024
        const val MAX_REQUEST_BYTES = 4 * 1024 * 1024
    }
}
