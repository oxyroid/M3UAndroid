package com.m3u.data.tv

import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import kotlin.random.Random

internal object Utils {
    fun findPort(): Int = ServerSocket(0).use { it.localPort }

    fun createPin(): Int = Random.nextInt(from = 100000, until = 999999)

    fun getLocalHostAddress(): String? = NetworkInterface
        .getNetworkInterfaces()
        .asSequence()
        .flatMap { networkInterface -> networkInterface.inetAddresses.asSequence() }
        .firstOrNull { it.isLocalAddress() }
        ?.hostAddress

    private fun InetAddress.isLocalAddress(): Boolean = runCatching {
        isSiteLocalAddress && !isLoopbackAddress && hostAddress?.contains(':') == false
    }.getOrDefault(false)
}
