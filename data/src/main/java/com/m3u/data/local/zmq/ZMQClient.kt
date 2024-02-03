package com.m3u.data.local.zmq

import com.m3u.core.architecture.logger.Logger
import com.m3u.core.architecture.logger.prefix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
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
        val socket = context.createSocket(SocketType.DEALER).apply {
            connect("tcp://$address:$publishPort")
            logger.log("subscribe")
        }
        launch {
            while (true) {
                logger.log("try receiving broadcast")
                val broadcast = socket?.recvAwait(0) ?: continue
                val string = String(broadcast, ZMQ.CHARSET)
                logger.log("receive a broadcast: $string")
                trySendBlocking(string)
            }
        }
        awaitClose {
            socket.disconnect("tcp://localhost:$publishPort")
            logger.log("unsubscribe")
        }
    }
        .flowOn(Dispatchers.IO)

    suspend fun sendRequest(body: String): String = suspendCoroutine { cont ->
        response.send(body)
        cont.resume(response.recvStr())
    }

    fun release() {
        response.disconnect("tcp://*:$responsePort")
    }
}
