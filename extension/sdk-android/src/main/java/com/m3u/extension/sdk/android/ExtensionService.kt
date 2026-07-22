package com.m3u.extension.sdk.android

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.ParcelFileDescriptor
import com.m3u.extension.api.InvocationId
import com.m3u.extension.api.SerializedExtensionEnvelope
import com.m3u.extension.api.SerializedExtensionResult
import com.m3u.extension.api.security.BrokerProtocolVersions
import com.m3u.extension.runtime.ExtensionTransport
import com.m3u.extension.transport.android.ExtensionHandshakeRequest
import com.m3u.extension.transport.android.ExtensionHandshakeError
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
    @Volatile
    private var negotiatedBrokerProtocolVersion: Int? = null

    /** Override when a hook needs the host broker. The default delegates to [transport]. */
    protected open suspend fun invoke(
        envelope: SerializedExtensionEnvelope,
        hostNetworkBroker: ExtensionHostNetworkBroker,
    ): SerializedExtensionResult = transport.invoke(envelope)

    private val binder = object : IExtensionService.Stub() {
        @Synchronized
        override fun handshake(request: ParcelFileDescriptor): ParcelFileDescriptor {
            val handshake = json.decodeFromString<ExtensionHandshakeRequest>(
                ParcelFileCodec.read(request, MAX_HANDSHAKE_BYTES)
            )
            val extensionApiRange = transport.manifest.apiRange
            val brokerProtocolVersion = BrokerProtocolVersions.negotiate(
                handshake.supportedBrokerProtocolVersions
            )
            val error = when {
                handshake.transportVersion != ExtensionProtocol.TRANSPORT_VERSION ->
                    ExtensionHandshakeError(
                        code = "transport.incompatible",
                        message = "Extension transport protocol is incompatible",
                    )
                handshake.hostApiVersion !in extensionApiRange -> ExtensionHandshakeError(
                    code = "api.incompatible",
                    message = "Host API version is outside the extension range",
                )
                brokerProtocolVersion == null -> ExtensionHandshakeError(
                    code = "broker.incompatible",
                    message = "Host and extension do not share a broker protocol version",
                )
                else -> null
            }
            if (error != null) {
                return ParcelFileCodec.write(
                    this@ExtensionService,
                    json.encodeToString(
                        ExtensionHandshakeResponse(
                            transportVersion = ExtensionProtocol.TRANSPORT_VERSION,
                            extensionApiRange = extensionApiRange,
                            error = error,
                        )
                    ),
                )
            }
            checkNotNull(brokerProtocolVersion)
            negotiatedBrokerProtocolVersion?.let { previous ->
                require(previous == brokerProtocolVersion) {
                    "Broker protocol changed during an active extension connection"
                }
            }
            negotiatedBrokerProtocolVersion = brokerProtocolVersion
            return ParcelFileCodec.write(
                this@ExtensionService,
                json.encodeToString(
                    ExtensionHandshakeResponse(
                        transportVersion = ExtensionProtocol.TRANSPORT_VERSION,
                        extensionApiRange = extensionApiRange,
                        brokerProtocolVersion = brokerProtocolVersion,
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
            val broker = ExtensionHostNetworkBroker(
                context = this@ExtensionService,
                bridge = hostBridge,
                json = json,
                brokerProtocolVersion = checkNotNull(negotiatedBrokerProtocolVersion) {
                    "Extension handshake must complete before invocation"
                },
            )
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
