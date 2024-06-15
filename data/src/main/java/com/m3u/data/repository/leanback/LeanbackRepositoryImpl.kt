package com.m3u.data.repository.leanback

import android.net.nsd.NsdServiceInfo
import androidx.compose.runtime.snapshotFlow
import com.m3u.core.architecture.Publisher
import com.m3u.core.architecture.dispatcher.Dispatcher
import com.m3u.core.architecture.dispatcher.M3uDispatchers.IO
import com.m3u.core.architecture.logger.Logger
import com.m3u.core.architecture.logger.Profiles
import com.m3u.core.architecture.logger.install
import com.m3u.core.architecture.preferences.Preferences
import com.m3u.core.util.coroutine.timeout
import com.m3u.core.wrapper.Resource
import com.m3u.core.wrapper.asResource
import com.m3u.data.api.LeanbackApiDelegate
import com.m3u.data.leanback.Utils
import com.m3u.data.leanback.http.HttpServer
import com.m3u.data.leanback.model.Leanback
import com.m3u.data.leanback.nsd.NsdDeviceManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration

class LeanbackRepositoryImpl @Inject constructor(
    private val nsdDeviceManager: NsdDeviceManager,
    private val httpServer: HttpServer,
    private val leanbackApi: LeanbackApiDelegate,
    logger: Logger,
    preferences: Preferences,
    publisher: Publisher,
    @Dispatcher(IO) ioDispatcher: CoroutineDispatcher
) : LeanbackRepository() {
    private val logger = logger.install(Profiles.REPOS_LEANBACK)
    private val leanback = publisher.leanback
    private val coroutineScope = CoroutineScope(ioDispatcher)

    init {
        snapshotFlow { preferences.remoteControl }
            .onEach { remoteControl ->
                when {
                    !remoteControl -> closeBroadcastOnLeanback()
                    leanback -> broadcastOnLeanback()
                    else -> closeBroadcastOnLeanback()
                }
            }
            .launchIn(coroutineScope)
    }

    private val _broadcastCodeOnLeanback = MutableStateFlow<Int?>(null)
    override val broadcastCodeOnLeanback = _broadcastCodeOnLeanback.asStateFlow()

    private var broadcastOnLeanbackJob: Job? = null

    override fun broadcastOnLeanback() {
        val serverPort = Utils.findPort()
        closeBroadcastOnLeanback()
        httpServer.start(serverPort)
        broadcastOnLeanbackJob = coroutineScope.launch {
            while (isActive) {
                val nsdPort = Utils.findPort()
                val pin = Utils.createPin()
                val host = Utils.getLocalHostAddress() ?: continue

                logger.log("start-server: server-port[$serverPort], nsd-port[$nsdPort], pin[$pin], host[$host]")

                nsdDeviceManager
                    .broadcast(
                        pin = pin,
                        port = nsdPort,
                        metadata = mapOf(
                            NsdDeviceManager.META_DATA_PORT to serverPort,
                            NsdDeviceManager.META_DATA_HOST to host
                        )
                    )
                    .onStart {
                        logger.log("start-server: opening...")
                    }
                    .onCompletion {
                        _broadcastCodeOnLeanback.value = null
                        logger.log("start-server: nsd completed")
                    }
                    .onEach { registered ->
                        logger.log("start-server: registered: $registered")
                        _broadcastCodeOnLeanback.value = if (registered != null) pin else null
                    }
                    .collect()
            }
        }
    }

    override fun closeBroadcastOnLeanback() {
        httpServer.stop()
        broadcastOnLeanbackJob?.cancel()
        broadcastOnLeanbackJob = null
    }

    private val _connected = MutableStateFlow<Leanback?>(null)
    override val connected: StateFlow<Leanback?> = _connected.asStateFlow()

    private var connectToLeanbackJob: Job? = null

    override fun connectToLeanback(
        broadcastCode: Int,
        timeout: Duration
    ): Flow<ConnectionToLeanbackValue> = channelFlow {
        val completed = nsdDeviceManager
            .search()
            .onStart { trySendBlocking(ConnectionToLeanbackValue.Searching) }
            .timeout(timeout) {
                logger.log("pair: timeout")
                trySendBlocking(ConnectionToLeanbackValue.Timeout)
            }
            .mapNotNull { all ->
                logger.log("pair: all devices: $all")
                val info = all.find {
                    it.getAttribute(NsdDeviceManager.META_DATA_PIN) == broadcastCode.toString()
                } ?: return@mapNotNull null
                val port = info
                    .getAttribute(NsdDeviceManager.META_DATA_PORT)
                    ?.toIntOrNull()
                    ?: return@mapNotNull null
                val host = info
                    .getAttribute(NsdDeviceManager.META_DATA_HOST)
                    ?: return@mapNotNull null

                ConnectionToLeanbackValue.Completed(host, port)
            }
            .firstOrNull()

        if (completed != null) {
            trySendBlocking(ConnectionToLeanbackValue.Connecting)
            connectToLeanbackJob?.cancel()
            connectToLeanbackJob = leanbackApi
                .prepare(completed.host, completed.port)
                .asResource()
                .onEach { resource ->
                    when (resource) {
                        Resource.Loading -> {
                            logger.log("pair: connecting")
                        }

                        is Resource.Success -> {
                            logger.log("pair: connected")
                            _connected.value = resource.data
                            trySendBlocking(completed)
                        }

                        is Resource.Failure -> {
                            logger.log("pair: catch an error, ${resource.message}")
                            _connected.value = null
                            trySendBlocking(ConnectionToLeanbackValue.Idle(resource.message))
                        }
                    }
                }
                .launchIn(coroutineScope)
        }
        awaitClose {
            trySendBlocking(ConnectionToLeanbackValue.Idle())
        }
    }

    override suspend fun disconnectToLeanback() {
        connectToLeanbackJob?.cancel()
        connectToLeanbackJob = null
        _connected.value = null
    }

    private fun NsdServiceInfo.getAttribute(key: String): String? = attributes[key]?.decodeToString()
}
