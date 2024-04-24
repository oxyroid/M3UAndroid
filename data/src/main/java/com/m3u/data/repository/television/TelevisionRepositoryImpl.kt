package com.m3u.data.repository.television

import android.net.nsd.NsdServiceInfo
import androidx.compose.runtime.snapshotFlow
import com.m3u.core.architecture.Publisher
import com.m3u.core.architecture.dispatcher.Dispatcher
import com.m3u.core.architecture.dispatcher.M3uDispatchers.IO
import com.m3u.core.architecture.logger.Logger
import com.m3u.core.architecture.logger.Profiles
import com.m3u.core.architecture.logger.install
import com.m3u.core.architecture.preferences.Preferences
import com.m3u.core.util.coroutine.onTimeout
import com.m3u.core.wrapper.Resource
import com.m3u.core.wrapper.asResource
import com.m3u.data.api.LocalPreparedService
import com.m3u.data.television.Utils
import com.m3u.data.television.http.HttpServer
import com.m3u.data.television.model.Television
import com.m3u.data.television.nsd.NsdDeviceManager
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
import kotlinx.coroutines.flow.combine
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

class TelevisionRepositoryImpl @Inject constructor(
    private val nsdDeviceManager: NsdDeviceManager,
    private val server: HttpServer,
    private val localService: LocalPreparedService,
    logger: Logger,
    preferences: Preferences,
    publisher: Publisher,
    @Dispatcher(IO) ioDispatcher: CoroutineDispatcher
) : TelevisionRepository() {
    private val logger = logger.install(Profiles.REPOS_TELEVISION)
    private val isTelevision = publisher.isTelevision
    private val coroutineScope = CoroutineScope(ioDispatcher)

    init {
        combine(
            snapshotFlow { preferences.remoteControl },
            snapshotFlow { preferences.alwaysTv }
        ) { remoteControl, alwaysTv ->
            when {
                !remoteControl -> closeBroadcastOnTelevision()
                alwaysTv || isTelevision -> broadcastOnTelevision()
                else -> closeBroadcastOnTelevision()
            }
        }
            .launchIn(coroutineScope)
    }

    private val _broadcastCodeOnTelevision = MutableStateFlow<Int?>(null)
    override val broadcastCodeOnTelevision = _broadcastCodeOnTelevision.asStateFlow()

    private var broadcastOnTelevisionJob: Job? = null

    override fun broadcastOnTelevision() {
        val serverPort = Utils.findPort()
        closeBroadcastOnTelevision()
        server.start(serverPort)
        broadcastOnTelevisionJob = coroutineScope.launch {
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
                        _broadcastCodeOnTelevision.value = null
                        logger.log("start-server: nsd completed")
                    }
                    .onEach { registered ->
                        logger.log("start-server: registered: $registered")
                        _broadcastCodeOnTelevision.value = if (registered != null) pin else null
                    }
                    .collect()
            }
        }
    }

    override fun closeBroadcastOnTelevision() {
        server.stop()
        broadcastOnTelevisionJob?.cancel()
        broadcastOnTelevisionJob = null
    }

    private val _connected = MutableStateFlow<Television?>(null)
    override val connected: StateFlow<Television?> = _connected.asStateFlow()

    private var connectToTelevisionJob: Job? = null

    override fun connectToTelevision(
        broadcastCode: Int,
        timeout: Duration
    ): Flow<ConnectionToTelevisionValue> = channelFlow {
        val completed = nsdDeviceManager
            .search()
            .onStart { trySendBlocking(ConnectionToTelevisionValue.Searching) }
            .onTimeout(timeout) {
                logger.log("pair: timeout")
                trySendBlocking(ConnectionToTelevisionValue.Timeout)
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

                ConnectionToTelevisionValue.Completed(host, port)
            }
            .firstOrNull()

        if (completed != null) {
            trySendBlocking(ConnectionToTelevisionValue.Connecting)
            connectToTelevisionJob?.cancel()
            connectToTelevisionJob = localService
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
                            trySendBlocking(ConnectionToTelevisionValue.Idle(resource.message))
                        }
                    }
                }
                .launchIn(coroutineScope)
        }
        awaitClose {
            trySendBlocking(ConnectionToTelevisionValue.Idle())
        }
    }

    override suspend fun disconnectToTelevision() {
        connectToTelevisionJob?.cancel()
        connectToTelevisionJob = null
        _connected.value = null
    }

    private val _allUpdateStates = MutableStateFlow(emptyMap<UpdateKey, UpdateState>())
    override val allUpdateStates = _allUpdateStates.asStateFlow()

    private fun NsdServiceInfo.getAttribute(key: String): String? =
        attributes[key]?.decodeToString()
}
