package com.m3u.extension.transport.android

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.m3u.extension.api.ExtensionManifest
import com.m3u.extension.api.ExtensionApiVersion
import com.m3u.extension.api.ExtensionApiVersions
import com.m3u.extension.api.InvocationId
import com.m3u.extension.api.SerializedExtensionEnvelope
import com.m3u.extension.api.SerializedExtensionResult
import com.m3u.extension.runtime.ExtensionTransport
import com.m3u.extension.runtime.ExtensionTransportHealth
import com.m3u.extension.transport.android.ipc.IExtensionHostBridge
import com.m3u.extension.transport.android.ipc.IExtensionService
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AndroidBoundExtensionTransport private constructor(
    private val context: Context,
    private val connection: ServiceConnection,
    private val service: IExtensionService,
    override val manifest: ExtensionManifest,
    private val hostBridge: IExtensionHostBridge,
    private val json: Json,
) : ExtensionTransport, Closeable {
    private val available = AtomicBoolean(true)
    private val closed = AtomicBoolean(false)
    private val binderScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val deathRecipient = IBinder.DeathRecipient { available.set(false) }

    init {
        service.asBinder().linkToDeath(deathRecipient, 0)
    }

    override suspend fun invoke(request: SerializedExtensionEnvelope): SerializedExtensionResult =
        suspendCancellableCoroutine { continuation ->
            val binderJob = binderScope.launch {
                val result = runCatching {
                    check(available.get()) { "Extension service is unavailable" }
                    val requestFile = ParcelFileCodec.write(context, json.encodeToString(request))
                    val responseFile = service.invoke(
                        request.invocationId.value,
                        request.hook.id,
                        request.schemaVersion,
                        requestFile,
                        hostBridge,
                    )
                    json.decodeFromString<SerializedExtensionResult>(
                        ParcelFileCodec.read(responseFile, MAX_RESPONSE_BYTES)
                    )
                }
                if (continuation.isActive) continuation.resumeWith(result)
            }
            continuation.invokeOnCancellation {
                binderJob.cancel()
                binderScope.launch {
                    if (available.get()) runCatching { service.cancel(request.invocationId.value) }
                }
            }
        }

    override suspend fun cancel(invocationId: InvocationId) = withContext(Dispatchers.IO) {
        if (available.get()) service.cancel(invocationId.value)
    }

    override suspend fun health(): ExtensionTransportHealth = withContext(Dispatchers.IO) {
        if (!available.get()) return@withContext ExtensionTransportHealth.UNAVAILABLE
        when (service.health()) {
            "healthy" -> ExtensionTransportHealth.HEALTHY
            "degraded" -> ExtensionTransportHealth.DEGRADED
            else -> ExtensionTransportHealth.UNAVAILABLE
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            available.set(false)
            binderScope.cancel()
            runCatching { service.asBinder().unlinkToDeath(deathRecipient, 0) }
            runCatching { context.unbindService(connection) }
        }
    }

    companion object {
        private const val MAX_MANIFEST_BYTES = 256 * 1024
        private const val MAX_RESPONSE_BYTES = 4 * 1024 * 1024

        suspend fun connect(
            context: Context,
            installed: InstalledExtensionService,
            hostBridgeFactory: (ExtensionManifest) -> IExtensionHostBridge,
            hostApiVersion: ExtensionApiVersion = ExtensionApiVersions.Current,
            json: Json = Json { ignoreUnknownKeys = true; explicitNulls = false },
        ): AndroidBoundExtensionTransport {
            val appContext = context.applicationContext
            return suspendCancellableCoroutine { continuation ->
                val connectionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
                val intent = Intent(ExtensionProtocol.SERVICE_ACTION).setComponent(
                    ComponentName(installed.packageName, installed.serviceName)
                )
                lateinit var connection: ServiceConnection
                connection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                        connectionScope.launch {
                            runCatching {
                                val service = IExtensionService.Stub.asInterface(binder)
                                val handshake = json.decodeFromString<ExtensionHandshakeResponse>(
                                    ParcelFileCodec.read(
                                        service.handshake(
                                            ParcelFileCodec.write(
                                                appContext,
                                                json.encodeToString(
                                                    ExtensionHandshakeRequest(
                                                        ExtensionProtocol.TRANSPORT_VERSION,
                                                        hostApiVersion,
                                                    )
                                                )
                                            )
                                        ),
                                        MAX_MANIFEST_BYTES,
                                    )
                                )
                                require(handshake.transportVersion == ExtensionProtocol.TRANSPORT_VERSION) {
                                    "Extension transport protocol is incompatible"
                                }
                                val manifest = json.decodeFromString<ExtensionManifest>(
                                    ParcelFileCodec.read(service.openManifest(), MAX_MANIFEST_BYTES)
                                )
                                require(handshake.extensionApiRange == manifest.apiRange) {
                                    "Extension handshake does not match its manifest"
                                }
                                AndroidBoundExtensionTransport(
                                    appContext,
                                    connection,
                                    service,
                                    manifest,
                                    hostBridgeFactory(manifest),
                                    json,
                                )
                            }.onSuccess { transport ->
                                if (continuation.isActive) {
                                    continuation.resume(transport)
                                } else {
                                    transport.close()
                                }
                            }.onFailure { error ->
                                runCatching { appContext.unbindService(connection) }
                                if (continuation.isActive) continuation.resumeWithException(error)
                            }
                            connectionScope.cancel()
                        }
                    }

                    override fun onServiceDisconnected(name: ComponentName) = Unit
                }
                if (!appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)) {
                    connectionScope.cancel()
                    continuation.resumeWithException(IllegalStateException("Unable to bind extension service"))
                }
                continuation.invokeOnCancellation {
                    connectionScope.cancel()
                    runCatching { appContext.unbindService(connection) }
                }
            }
        }
    }
}
