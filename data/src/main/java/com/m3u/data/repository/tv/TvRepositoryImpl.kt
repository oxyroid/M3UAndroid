package com.m3u.data.repository.tv

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
import com.m3u.data.api.TvApiDelegate
import com.m3u.data.tv.Utils
import com.m3u.data.tv.http.HttpServer
import com.m3u.data.tv.model.TvInfo
import com.m3u.data.tv.nsd.NsdDeviceManager
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

class TvRepositoryImpl @Inject constructor(
    private val nsdDeviceManager: NsdDeviceManager,
    private val httpServer: HttpServer,
    private val tvApi: TvApiDelegate,
    logger: Logger,
    preferences: Preferences,
    publisher: Publisher,
    @Dispatcher(IO) ioDispatcher: CoroutineDispatcher
) : TvRepository() {
    private val logger = logger.install(Profiles.REPOS_LEANBACK)
    private val tv = publisher.tv
    private val coroutineScope = CoroutineScope(ioDispatcher)

    init {
        snapshotFlow { preferences.remoteControl }
            .onEach { remoteControl ->
                when {
                    !remoteControl -> closeBroadcastOnTv()
                    tv -> broadcastOnTv()
                    else -> closeBroadcastOnTv()
                }
            }
            .launchIn(coroutineScope)
    }

    private val _broadcastCodeOnTv = MutableStateFlow<Int?>(null)
    override val broadcastCodeOnTv = _broadcastCodeOnTv.asStateFlow()

    private var broadcastOnTvJob: Job? = null

    override fun broadcastOnTv() {
        val serverPort = Utils.findPort()
        closeBroadcastOnTv()
        httpServer.start(serverPort)
        broadcastOnTvJob = coroutineScope.launch {
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
                        _broadcastCodeOnTv.value = null
                        logger.log("start-server: nsd completed")
                    }
                    .onEach { registered ->
                        logger.log("start-server: registered: $registered")
                        _broadcastCodeOnTv.value = if (registered != null) pin else null
                    }
                    .collect()
            }
        }
    }

    override fun closeBroadcastOnTv() {
        httpServer.stop()
        broadcastOnTvJob?.cancel()
        broadcastOnTvJob = null
    }

    private val _connected = MutableStateFlow<TvInfo?>(null)
    override val connected: StateFlow<TvInfo?> = _connected.asStateFlow()

    private var connectToTvJob: Job? = null

    override fun connectToTv(
        broadcastCode: Int,
        timeout: Duration
    ): Flow<ConnectionToTvValue> = channelFlow {
        val completed = nsdDeviceManager
            .search()
            .onStart { trySendBlocking(ConnectionToTvValue.Searching) }
            .timeout(timeout) {
                logger.log("pair: timeout")
                trySendBlocking(ConnectionToTvValue.Timeout)
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

                ConnectionToTvValue.Completed(host, port)
            }
            .firstOrNull()

        if (completed != null) {
            trySendBlocking(ConnectionToTvValue.Connecting)
            connectToTvJob?.cancel()
            connectToTvJob = tvApi
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
                            trySendBlocking(ConnectionToTvValue.Idle(resource.message))
                        }
                    }
                }
                .launchIn(coroutineScope)
        }
        awaitClose {
            trySendBlocking(ConnectionToTvValue.Idle())
        }
    }

    override suspend fun disconnectToTv() {
        connectToTvJob?.cancel()
        connectToTvJob = null
        _connected.value = null
    }

    private fun NsdServiceInfo.getAttribute(key: String): String? = attributes[key]?.decodeToString()
}
