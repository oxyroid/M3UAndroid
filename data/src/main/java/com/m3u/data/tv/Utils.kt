package com.m3u.data.tv

import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import kotlin.random.Random

internal object Utils {
    fun findPort(): Int = ServerSocket(0).use { it.localPort }
    fun getLocalHostAddress(): String? = NetworkInterface
        .getNetworkInterfaces()
        .iterator()
        .asSequence()
        .flatMap { networkInterface ->
            networkInterface.inetAddresses
                .asSequence()
                .filter { !it.isLoopbackAddress }
        }
        .toList()
        .filter { it.isLocalAddress() }
        .map { it.hostAddress }
        .firstOrNull()

    fun createPin(): Int = Random.nextInt(999999)
    private fun InetAddress.isLocalAddress(): Boolean {
        try {
            return isSiteLocalAddress
                    && !hostAddress!!.contains(":")
                    && hostAddress != "127.0.0.1"
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }
}