package com.m3u.data

import android.net.ConnectivityManager
import android.net.LinkProperties
import java.net.ServerSocket
import kotlin.random.Random

object Locals {
    fun findPort(): Int = ServerSocket(0).use { it.localPort }
    fun getHost(connectivityManager: ConnectivityManager): String {
        val properties = connectivityManager
            .getLinkProperties(connectivityManager.activeNetwork) as LinkProperties
        val addresses = properties.linkAddresses
        return addresses
            .find { it.address.isSiteLocalAddress }
            ?.address?.hostAddress.orEmpty()
    }

    fun createPin(): Int = Random.nextInt(999999)
}