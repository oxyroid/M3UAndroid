package com.m3u.data.net.udp

import com.m3u.data.net.udp.action.Action
import kotlinx.serialization.json.Json
import java.net.DatagramPacket
import java.net.InetAddress
import java.nio.charset.Charset

class UdpPacket private constructor(
    val name: String,
    val side: String,
    private val action: Action,
    private val metadata: String
) {
    fun find(arg: String): String? = action.find(metadata, arg)
    fun toDatagramPacket(
        address: InetAddress,
        port: Int
    ): DatagramPacket {
        val bytes = metadata.toByteArray()
        return with(bytes) { DatagramPacket(this, size, address, port) }
    }

    companion object {
        val JSON = Json
        const val SIDE_SERVER = "server"
        const val SIDE_CLIENT = "client"
        fun fromByteArray(bytes: ByteArray): UdpPacket {
            val text = bytes.toString(Charset.defaultCharset())
            return JSON.decodeFromString(text)
        }

        fun create(
            action: Action,
            side: String,
            name: String = "M3U",
            builder: Action.() -> Map<String, *>
        ): UdpPacket {
            return UdpPacket(
                name = name,
                side = side,
                action = action,
                metadata = action.createMetadata(builder(action))
            )
        }
    }

}
