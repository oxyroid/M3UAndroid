package com.m3u.data.manager.zmq

import android.os.Build
import com.m3u.core.architecture.logger.Logger
import com.m3u.core.architecture.logger.prefix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.zeromq.SocketType
import org.zeromq.ZContext
import org.zeromq.ZMQ
import kotlin.random.Random

class ZMQServer(
    val responsePort: Int = createPort(),
    val publishPort: Int = responsePort + 1,
    logger: Logger
) {
    private var context: ZContext? = null
    private var response: ZMQ.Socket? = null
    private var publish: ZMQ.Socket? = null
    private val logger = logger.prefix("zmq-server")
    private val model = Build.MODEL

    private val _request = MutableSharedFlow<String>()
    val request: SharedFlow<String> = _request.asSharedFlow()

    private var responseJob: Job? = null

    suspend fun start() {
        withContext(Dispatchers.IO) {
            context = ZContext()
            response = context!!.createSocket(SocketType.REP).apply {
                bind("tcp://*:$responsePort")
                logger.log("bind response")
            }
            publish = context!!.createSocket(SocketType.ROUTER).apply {
                setHelloMsg(model.toByteArray(ZMQ.CHARSET))
                bind("tcp://*:$publishPort")
                logger.log("bind publish")
            }
            responseJob = launch {
                while (true) {
                    val reply = response?.recvAwait(0) ?: continue
                    _request.emit(String(reply, ZMQ.CHARSET))
                }
            }
        }
        logger.log("start completed")
    }

    fun release() {
        responseJob?.cancel()
        responseJob = null
        response?.unbind("tcp://*:$responsePort")
        response = null
        publish?.unbind("tcp://*:$publishPort")
        publish = null
        context?.destroy()
        context = null
        logger.log("release")
    }


    fun reply(body: String) {
        response?.send(body)
        logger.log("reply, $body")
    }

    fun broadcast(topic: String, body: String) {
        publish?.send("$topic-$body".toByteArray(ZMQ.CHARSET))
        logger.log("broadcast, $body")
    }

    companion object {
        fun createPort(): Int = Random.nextInt(400, 62000)
    }
}