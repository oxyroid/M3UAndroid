package com.m3u.data.repository.impl

import android.net.nsd.NsdServiceInfo
import com.m3u.core.architecture.logger.Logger
import com.m3u.core.architecture.logger.prefix
import com.m3u.data.manager.nsd.NsdDeviceManager
import com.m3u.data.net.zmq.ZMQClient
import com.m3u.data.net.zmq.ZMQServer
import com.m3u.data.repository.PairClientState
import com.m3u.data.repository.PairServerState
import com.m3u.data.repository.TvRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.timeout
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration

class TvRepositoryImpl @Inject constructor(
    private val nsdDeviceManager: NsdDeviceManager,
    logger: Logger
) : TvRepository {
    private val logger = logger.prefix("tv-repos")
    private val server: ZMQServer by lazy {
        ZMQServer(
            logger = logger
        )
    }

    @Volatile
    private var client: ZMQClient? = null

    /**
     * Television used, request from phone:
     * ```kotlin
     * televisionScope.launch {
     *     serverJob?.cancel()
     *     serverJob = launch { repository.startServer() }
     *     // ...
     *     repository.fromPhone.collect { request ->
     *         val response = getResponseByRequest(request)
     *         repository.reply(response)
     *     }
     * }
     * ```
     */
    override val fromPhone: SharedFlow<String> by lazy { server.request }

    override suspend fun toTelevision(body: String): String {
        val client = checkNotNull(client)
        return client.sendRequest(body)
    }

    /**
     * Phone used, broadcast from tv:
     * ```kotlin
     * phoneScope.launch {
     *     clientJob?.cancel()
     *     clientJob = launch { repository.startClient() }
     *     repository.broadcast.collect { broadcast ->
     *         showBroadcast(broadcast)
     *     }
     * }
     * ```
     */
    private val _broadcast = MutableSharedFlow<String>()
    override val broadcast: SharedFlow<String> = _broadcast.asSharedFlow()

    /**
     * Television used, pair state:
     * ```kotlin
     * televisionScope.launch {
     *     serverJob?.cancel()
     *     serverJob = launch { repository.startServer() }
     *     repository.pairServerState.onEach { state ->
     *         showPairState(state)
     *     }.launchIn(this)
     *     // ...
     * }
     * ```
     */
    private val _pairServerState = MutableStateFlow<PairServerState>(PairServerState.Idle)
    override val pairServerState = _pairServerState.asStateFlow()

    /**
     * Phone used, pair state:
     * ```kotlin
     * phoneScope.launch {
     *     repository.pairClientState.onEach { state ->
     *         saveAndShowPairState(state)
     *         when (state) {
     *             PairClientState.Idle -> {
     *                 clientJob?.cancel()
     *                 clientJob = null
     *             }
     *             is PairClientState.Connected -> {
     *                 clientJob = launch { repository.startClient() }
     *             }
     *         }
     *     }.launchIn(this)
     * }
     * ```
     * User type PIN on phone from television and then pair it.
     * ```kotlin
     * fun pair(pin: Int) { phoneScope.launch { repository.pair(pin) } }
     * ```
     */
    private val _pairClientState = MutableStateFlow<PairClientState>(PairClientState.Idle)
    override val pairClientState = _pairClientState.asStateFlow()

    override suspend fun startServer(): Unit = coroutineScope {
        launch { server.start() }
        val publishPort = server.publishPort
        val responsePort = server.responsePort
        val pin = NsdDeviceManager.createPin()
        nsdDeviceManager
            .broadcast(
                pin = pin,
                metadata = mapOf(
                    NsdDeviceManager.META_DATA_PUB_PORT to publishPort,
                    NsdDeviceManager.META_DATA_REP_PORT to responsePort
                )
            )
            .onStart {
                _pairServerState.value = PairServerState.Prepared(pin)
            }
            .onCompletion {
                _pairServerState.value = PairServerState.Idle
            }
            .onEach { connected ->
                _pairServerState.value = when (connected) {
                    null -> PairServerState.Prepared(pin)
                    else -> PairServerState.Connected(connected)
                }
            }
            .launchIn(this)
    }

    @OptIn(FlowPreview::class)
    override suspend fun pair(pin: Int, timeout: Duration): Unit = coroutineScope {
        nsdDeviceManager
            .search()
            .timeout(timeout)
            .onStart { _pairClientState.value = PairClientState.Connecting }
            .catch {
                if (it is TimeoutCancellationException) {
                    _pairClientState.value = PairClientState.Idle
                }
            }
            .onEach { all ->
//                val info = all
//                    .find {
//                        it.getAttribute(NsdDeviceManager.META_DATA_PIN) == pin.toString()
//                    } ?: return@onEach
                val info = all.firstOrNull() ?: return@onEach
                val pubPort =
                    info.getAttribute(NsdDeviceManager.META_DATA_PUB_PORT) ?: return@onEach
                val repPort =
                    info.getAttribute(NsdDeviceManager.META_DATA_REP_PORT) ?: return@onEach
                val address = info.host.hostAddress.orEmpty()
                client = ZMQClient(
                    address = address,
                    responsePort = repPort.toInt(),
                    publishPort = pubPort.toInt(),
                    logger = logger.delegate
                )
                logger.log("pair success")
                _pairClientState.value = PairClientState.Connected(info)
                cancel()
            }
            .launchIn(this)
    }

    private var startClientJob: Job? = null
    override suspend fun startClient(): Unit = coroutineScope {
        logger.log("start client")
        val client = checkNotNull(client)
        startClientJob = client
            .subscribe()
            .onEach { _broadcast.emit(it) }
            .launchIn(this)
    }

    override fun stopClient() {
        startClientJob?.cancel()
        startClientJob = null
        _pairClientState.value = PairClientState.Idle
    }

    override fun release() {
        stopClient()
        client?.release()
        server.release()
    }

    private fun NsdServiceInfo.getAttribute(key: String): String? =
        attributes[key]?.decodeToString()
}
