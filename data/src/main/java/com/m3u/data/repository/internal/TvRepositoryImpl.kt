package com.m3u.data.repository.internal

import android.net.ConnectivityManager
import android.net.nsd.NsdServiceInfo
import com.m3u.core.architecture.logger.Logger
import com.m3u.core.architecture.logger.prefix
import com.m3u.core.util.coroutine.onTimeout
import com.m3u.data.Locals
import com.m3u.data.local.endpoint.HttpServer
import com.m3u.data.local.nsd.NsdDeviceManager
import com.m3u.data.repository.PairState
import com.m3u.data.repository.TvRepository
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject
import kotlin.time.Duration

class TvRepositoryImpl @Inject constructor(
    private val nsdDeviceManager: NsdDeviceManager,
    private val connectivityManager: ConnectivityManager,
    private val server: HttpServer,
    logger: Logger
) : TvRepository {
    private val logger = logger.prefix("tv-repos")

    override fun startServer(): Flow<Int?> = channelFlow {
        val serverPort = Locals.findPort()
        val nsdPort = Locals.findPort()
        val pin = Locals.createPin()
        val address = Locals.getAddress(connectivityManager)

        logger.log("start-server: server-port[$serverPort], nsd-port[$nsdPort], pin[$pin], address[$address]")

        server
            .start(serverPort)
            .launchIn(this)

        nsdDeviceManager
            .broadcast(
                pin = pin,
                port = nsdPort,
                metadata = mapOf(
                    NsdDeviceManager.META_DATA_PORT to serverPort,
                    NsdDeviceManager.META_DATA_ADDRESS to address
                )
            )
            .onStart {
                trySendBlocking(serverPort)
            }
            .onCompletion {
                trySendBlocking(null)
                logger.log("start-server: timeout!")
            }
            .onEach { connected ->
                trySendBlocking(
                    if (connected != null) null else serverPort
                )
            }
            .launchIn(this)

        awaitClose {
            logger.log("start-server: closing...")
        }
    }

    override fun pair(pin: Int, timeout: Duration): Flow<PairState> = channelFlow {
        trySendBlocking(PairState.Idle)
        nsdDeviceManager
            .search()
            .onStart { trySendBlocking(PairState.Connecting) }
            .onTimeout(timeout) { trySendBlocking(PairState.Timeout) }
            .onEach { all ->
                val info = all
                    .find {
                        it.getAttribute(NsdDeviceManager.META_DATA_PIN) == pin.toString()
                    } ?: return@onEach
                val port = info.getAttribute(NsdDeviceManager.META_DATA_PORT)
                    ?.toIntOrNull()
                    ?: return@onEach
                val address =
                    info.getAttribute(NsdDeviceManager.META_DATA_ADDRESS) ?: return@onEach

                trySendBlocking(PairState.Connected(address, port))
                cancel()
            }
            .launchIn(this)
    }

    override fun release() {
    }

    private fun NsdServiceInfo.getAttribute(key: String): String? =
        attributes[key]?.decodeToString()
}
