package com.m3u.data.local.zmq

import com.m3u.core.architecture.logger.Logger
import com.m3u.core.architecture.logger.prefix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.zeromq.SocketType
import org.zeromq.ZContext
import org.zeromq.ZMQ
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class ZMQClient(
    private val address: String,
    private val responsePort: Int,
    private val publishPort: Int,
    logger: Logger
) {
    private val context: ZContext by lazy { ZContext() }
    private val logger = logger.prefix("zmq-client")

    private val response: ZMQ.Socket by lazy {
        context.createSocket(SocketType.REQ).apply {
            connect("tcp://$address:$responsePort")
        }
    }

    fun subscribe() = channelFlow {
        val socket = context.createSocket(SocketType.SUB).apply {
            connect("tcp://$address:$publishPort")
            subscribe(ZMQ.SUBSCRIPTION_ALL)
            logger.log("Subscribed to broadcasts")
        }
        launch {
            while (true) {
                logger.log("Trying to receive broadcast")
                val broadcast = socket.recvStr() ?: continue
                logger.log("Received broadcast: $broadcast")
                trySendBlocking(broadcast)
            }
        }
        awaitClose {
            socket.disconnect("tcp://$address:$publishPort")
            logger.log("Unsubscribed from broadcasts")
        }
    }
        .flowOn(Dispatchers.IO)

    suspend fun sendRequest(body: String): String = suspendCoroutine { cont ->
        response.send(body)
        cont.resume(response.recvStr())
    }

    suspend inline fun <reified P, reified R> sendRequest(body: P): R =
        sendRequest(Json.encodeToString(body))
            .let { Json.decodeFromString(it) }

    fun release() {
        response.disconnect("tcp://$address:$responsePort")
        context.destroy()
    }
}
