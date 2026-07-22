package com.m3u.extension.transport.android

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.m3u.extension.api.ExtensionManifest
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
import kotlinx.coroutines.Dispatchers
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
    private val deathRecipient = IBinder.DeathRecipient { available.set(false) }

    init {
        service.asBinder().linkToDeath(deathRecipient, 0)
    }

    override suspend fun invoke(request: SerializedExtensionEnvelope): SerializedExtensionResult = withContext(Dispatchers.IO) {
        check(available.get()) { "Extension service is unavailable" }
        val requestFile = ParcelFileCodec.write(context, json.encodeToString(request))
        val responseFile = service.invoke(
            request.invocationId.value,
            request.hook.id,
            request.schemaVersion,
            requestFile,
            hostBridge,
        )
        json.decodeFromString(ParcelFileCodec.read(responseFile, MAX_RESPONSE_BYTES))
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
        if (available.getAndSet(false)) {
            service.asBinder().unlinkToDeath(deathRecipient, 0)
            context.unbindService(connection)
        }
    }

    companion object {
        private const val MAX_MANIFEST_BYTES = 256 * 1024
        private const val MAX_RESPONSE_BYTES = 4 * 1024 * 1024

        suspend fun connect(
            context: Context,
            installed: InstalledExtensionService,
            hostBridgeFactory: (ExtensionManifest) -> IExtensionHostBridge,
            json: Json = Json { ignoreUnknownKeys = true; explicitNulls = false },
        ): AndroidBoundExtensionTransport {
            val appContext = context.applicationContext
            return suspendCancellableCoroutine { continuation ->
                val intent = Intent(ExtensionProtocol.SERVICE_ACTION).setComponent(
                    ComponentName(installed.packageName, installed.serviceName)
                )
                lateinit var connection: ServiceConnection
                connection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                        runCatching {
                            val service = IExtensionService.Stub.asInterface(binder)
                            val manifest = json.decodeFromString<ExtensionManifest>(
                                ParcelFileCodec.read(service.openManifest(), MAX_MANIFEST_BYTES)
                            )
                            AndroidBoundExtensionTransport(
                                appContext,
                                connection,
                                service,
                                manifest,
                                hostBridgeFactory(manifest),
                                json,
                            )
                        }.onSuccess(continuation::resume)
                            .onFailure(continuation::resumeWithException)
                    }

                    override fun onServiceDisconnected(name: ComponentName) = Unit
                }
                if (!appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)) {
                    continuation.resumeWithException(IllegalStateException("Unable to bind extension service"))
                }
                continuation.invokeOnCancellation { runCatching { appContext.unbindService(connection) } }
            }
        }
    }
}
