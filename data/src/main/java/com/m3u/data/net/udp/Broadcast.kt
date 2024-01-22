package com.m3u.data.net.udp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

interface Broadcast {
    fun send(bytes: ByteArray)
    fun receive(): Flow<ByteArray>
}

data class LocalCode(
    val host: String,
    val port: Int,
    val code: Int,
    val expiration: Long
) {
    companion object {
        fun encode(code: LocalCode): ByteArray =
            "#M3U_LOCAL_CODE,${code.host},${code.port},${code.code},${code.expiration}#"
                .toByteArray(Charsets.UTF_8)

        fun decode(bytes: ByteArray): LocalCode {
            val line = bytes
                .toString(Charsets.UTF_8)
                .run {
                    take(indexOf('#', 1))
                }
            check(line.startsWith("#M3U_LOCAL_CODE", true)) {
                "Invalidate bytes: $line"
            }
            val elements = line.split(",")
            return LocalCode(
                host = elements[1],
                port = elements[2].toInt(),
                code = elements[3].toInt(),
                expiration = elements[4].toLong()
            )
        }
    }
}

object LocalCodeBroadcast : Broadcast {
    override fun send(bytes: ByteArray) {
        val socket = DatagramSocket()
        socket.broadcast = true
        val packet = DatagramPacket(bytes, bytes.size, InetAddress.getByName("0.0.0.0"), PORT)
        try {
            socket.send(packet)
        } catch (ignore: Exception) {
        }
    }

    override fun receive(): Flow<ByteArray> = channelFlow {
        val socket = DatagramSocket(PORT, InetAddress.getByName("0.0.0.0"))
        socket.broadcast = true
        awaitClose {
            try {
                socket.close()
            } catch (ignore: Exception) {
            }
        }
        while (true) {
            val bytes = ByteArray(65535)
            val packet = DatagramPacket(bytes, bytes.size)
            try {
                socket.receive(packet)
                send(packet.data)
            } catch (ignore: Exception) {
            }
        }
    }
        .flowOn(Dispatchers.IO)


    private const val PORT = 56981
}