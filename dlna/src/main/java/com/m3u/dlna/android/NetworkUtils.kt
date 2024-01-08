@file:Suppress("DEPRECATION")
package com.m3u.dlna.android

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkInfo
import org.jupnp.model.ModelUtil
import java.util.logging.Logger

object NetworkUtils {
    private val log: Logger = Logger.getLogger(NetworkUtils::class.java.name)

    fun getConnectedNetworkInfo(context: Context): NetworkInfo? {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        var networkInfo = connectivityManager.activeNetworkInfo
        if (networkInfo != null && networkInfo.isAvailable && networkInfo.isConnected) {
            return networkInfo
        }

        networkInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI)
        if (networkInfo != null && networkInfo.isAvailable && networkInfo.isConnected) return networkInfo

        networkInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE)
        if (networkInfo != null && networkInfo.isAvailable && networkInfo.isConnected) return networkInfo

        networkInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIMAX)
        if (networkInfo != null && networkInfo.isAvailable && networkInfo.isConnected) return networkInfo

        networkInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_ETHERNET)
        if (networkInfo != null && networkInfo.isAvailable && networkInfo.isConnected) return networkInfo

        log.info("Could not find any connected network...")

        return null
    }

    fun isEthernet(networkInfo: NetworkInfo?): Boolean {
        return isNetworkType(networkInfo, ConnectivityManager.TYPE_ETHERNET)
    }

    fun isWifi(networkInfo: NetworkInfo?): Boolean {
        return isNetworkType(
            networkInfo,
            ConnectivityManager.TYPE_WIFI
        ) || ModelUtil.ANDROID_EMULATOR
    }

    fun isMobile(networkInfo: NetworkInfo?): Boolean {
        return isNetworkType(networkInfo, ConnectivityManager.TYPE_MOBILE) || isNetworkType(
            networkInfo,
            ConnectivityManager.TYPE_WIMAX
        )
    }

    fun isNetworkType(networkInfo: NetworkInfo?, type: Int): Boolean {
        return networkInfo != null && networkInfo.type == type
    }

    fun isSSDPAwareNetwork(networkInfo: NetworkInfo?): Boolean {
        return isWifi(networkInfo) || isEthernet(networkInfo)
    }
}