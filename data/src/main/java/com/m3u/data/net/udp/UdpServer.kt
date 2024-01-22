package com.m3u.data.net.udp

import androidx.annotation.IntRange
import com.m3u.data.net.udp.action.VerifyBroadcast
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.random.Random

class UdpServer {
    companion object {
        const val PORT = 8393
    }

    fun startVerifyBroadcast(@IntRange(0L, 999999L) code: Int) {
        DatagramSocket().apply {
            broadcast = true
        }.use { socket ->
            val address = InetAddress.getByName("255.255.255.255")

            socket.send(
                UdpPacket.create(VerifyBroadcast, UdpPacket.SIDE_SERVER) {
                    mapOf(
                        VerifyBroadcast.ARG_VERITY_CODE to code
                    )
                }
                    .toDatagramPacket(address, PORT)
            )
        }
    }

    fun createVerifyCode(): Int {
        return Random.nextInt(999999)
    }
}