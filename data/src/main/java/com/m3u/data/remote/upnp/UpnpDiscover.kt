package com.m3u.data.remote.upnp

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class UpnpDiscover {
    private val address = InetAddress.getByName("239.255.255.250")
    private val port = 1900
    private val query = """
        M-SEARCH * HTTP/1.1
        HOST: $address:$port
        MAN: "ssdp:discover"
        MX: 3
        ST: ssdp:all
    """.trimIndent()

    private val socket = DatagramSocket().apply {
        soTimeout = 5000
        broadcast = true
    }

    fun start(): Flow<String> = flow {
        val request = DatagramPacket(query.toByteArray(), query.length, address, port)
        socket.send(request)
        val bytes = ByteArray(4096)
        val response = DatagramPacket(bytes, bytes.size)
        while (true) {
            try {
                socket.receive(response)
                emit(String(response.data, 0, response.length))
            } catch (e: Exception) {
                return@flow
            }
        }
    }
        .flowOn(Dispatchers.IO)
}