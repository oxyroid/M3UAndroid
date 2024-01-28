package com.m3u.data.manager.zmq

import org.zeromq.ZMQ
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

suspend fun ZMQ.Socket.recvAwait(flags: Int = 0): ByteArray = suspendCoroutine { cont ->
    cont.resume(recv(flags))
}