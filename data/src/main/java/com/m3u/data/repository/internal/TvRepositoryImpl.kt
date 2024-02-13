package com.m3u.data.repository.internal

import android.net.nsd.NsdServiceInfo
import com.m3u.core.architecture.Publisher
import com.m3u.core.architecture.dispatcher.Dispatcher
import com.m3u.core.architecture.dispatcher.M3uDispatchers.IO
import com.m3u.core.architecture.logger.Logger
import com.m3u.core.architecture.logger.prefix
import com.m3u.core.architecture.pref.Pref
import com.m3u.core.architecture.pref.observeAsFlow
import com.m3u.core.util.coroutine.onTimeout
import com.m3u.data.local.http.internal.Utils
import com.m3u.data.api.LocalPreparedService
import com.m3u.data.local.http.HttpServer
import com.m3u.data.local.nsd.NsdDeviceManager
import com.m3u.data.repository.PairState
import com.m3u.data.repository.TvRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration

class TvRepositoryImpl @Inject constructor(
    private val nsdDeviceManager: NsdDeviceManager,
    private val server: HttpServer,
    private val localService: LocalPreparedService,
    logger: Logger,
    pref: Pref,
    publisher: Publisher,
    @Dispatcher(IO) ioDispatcher: CoroutineDispatcher
) : TvRepository() {
    private val logger = logger.prefix("tv-repos")
    private val isTelevision = publisher.isTelevision
    private val coroutineScope = CoroutineScope(ioDispatcher)

    init {
        pref
            .observeAsFlow { it.remoteControl }
            .onEach { remoteControl ->
                when {
                    remoteControl && (isTelevision || pref.alwaysTv) -> startForServer()
                    else -> stopForServer()
                }
            }
            .launchIn(coroutineScope)
    }

    private val _pinCodeForServer = MutableStateFlow<Int?>(null)
    override val pinCodeForServer: StateFlow<Int?> = _pinCodeForServer.asStateFlow()

    private var serverJob: Job? = null
    override fun startForServer() {
        val serverPort = Utils.findPort()
        stopForServer()
        server.start(serverPort)
        serverJob = coroutineScope.launch {
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
                        _pinCodeForServer.value = null
                        logger.log("start-server: nsd completed")
                    }
                    .onEach { registered ->
                        logger.log("start-server: registered: $registered")
                        _pinCodeForServer.value = if (registered != null) pin else null
                    }
                    .collect()
            }
        }
    }

    override fun stopForServer() {
        server.stop()
        serverJob?.cancel()
        serverJob = null
    }

    override fun pairForClient(pin: Int, timeout: Duration): Flow<PairState> = channelFlow {
        trySendBlocking(PairState.Idle)
        nsdDeviceManager
            .search()
            .onStart {
                logger.log("pair: start")
                trySendBlocking(PairState.Connecting)
            }
            .onTimeout(timeout) {
                logger.log("pair: timeout")
                trySendBlocking(PairState.Timeout)
            }
            .onEach { all ->
                logger.log("pair: all devices: $all")
                val info = all.find {
                    it.getAttribute(NsdDeviceManager.META_DATA_PIN) == pin.toString()
                } ?: return@onEach
                val port = info.getAttribute(NsdDeviceManager.META_DATA_PORT)
                    ?.toIntOrNull()
                    ?: return@onEach
                val host =
                    info.getAttribute(NsdDeviceManager.META_DATA_HOST) ?: return@onEach

                logger.log("pair: connected")
                trySendBlocking(PairState.Connected(host, port))
                localService.prepare(host, port)
                cancel()
            }
            .launchIn(this)
    }

    private fun NsdServiceInfo.getAttribute(key: String): String? =
        attributes[key]?.decodeToString()
}
