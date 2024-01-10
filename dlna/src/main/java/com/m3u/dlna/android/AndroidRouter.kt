@file:Suppress("DEPRECATION")

package com.m3u.dlna.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.net.wifi.WifiManager
import com.m3u.dlna.android.NetworkUtils.getConnectedNetworkInfo
import org.jupnp.UpnpServiceConfiguration
import org.jupnp.model.ModelUtil
import org.jupnp.protocol.ProtocolFactory
import org.jupnp.transport.RouterImpl

open class AndroidRouter(
    configuration: UpnpServiceConfiguration,
    protocolFactory: ProtocolFactory,
    private val context: Context
) : RouterImpl(configuration, protocolFactory) {
    private val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
    var networkInfo = getConnectedNetworkInfo(context)
        protected set
    val isMobile: Boolean get() = NetworkUtils.isMobile(networkInfo)
    val isWifi: Boolean get() = NetworkUtils.isWifi(networkInfo)
    val isEthernet: Boolean get() = NetworkUtils.isEthernet(networkInfo)

    private var broadcastReceiver: BroadcastReceiver? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    init {
        if (!ModelUtil.ANDROID_EMULATOR) {
            broadcastReceiver = ConnectivityBroadcastReceiver()
            context.registerReceiver(
                broadcastReceiver,
                IntentFilter("android.net.conn.CONNECTIVITY_CHANGE")
            )
        }
    }

    override fun getLockTimeoutMillis(): Int = 15000

    override fun shutdown() {
        super.shutdown()
        unregisterBroadcastReceiver()
    }

    fun unregisterBroadcastReceiver() {
        broadcastReceiver?.let { context.unregisterReceiver(it) }
        broadcastReceiver = null
    }

    override fun enable(): Boolean {
        lock(writeLock)
        try {
            return super.enable().also { enabled ->
                if (enabled && isWifi) {
                    setWiFiMulticastLock(true)
                    setWifiLock(true)
                }
            }
        } finally {
            unlock(writeLock)
        }
    }

    override fun disable(): Boolean {
        lock(writeLock)
        try {
            if (isWifi) {
                setWiFiMulticastLock(false)
                setWifiLock(false)
            }
            return super.disable()
        } finally {
            unlock(writeLock)
        }
    }

    fun enableWifi(): Boolean {
        return try {
            wifiManager.setWifiEnabled(true)
        } catch (t: Throwable) {
            false
        }
    }

    protected fun setWiFiMulticastLock(enable: Boolean) {
        if (multicastLock == null) {
            multicastLock = wifiManager.createMulticastLock(javaClass.simpleName)
        }
        val lock = multicastLock!!
        if (enable) {
            if (!lock.isHeld) {
                lock.acquire()
            }
        } else {
            if (lock.isHeld) {
                lock.release();
            }
        }
    }

    protected fun setWifiLock(enable: Boolean) {
        if (wifiLock == null) {
            wifiLock = wifiManager.createWifiLock(
                WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                javaClass.simpleName
            )
        }
        val lock = wifiLock!!
        if (enable) {
            if (!lock.isHeld) {
                lock.acquire()
            }
        } else {
            if (lock.isHeld) {
                lock.release();
            }
        }
    }

    inner class ConnectivityBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ConnectivityManager.CONNECTIVITY_ACTION) return
            displayIntentInfo(intent)
            var networkInfo = getConnectedNetworkInfo(context)
            if (this@AndroidRouter.networkInfo != null && networkInfo == null) {
                for (i in 1..3) {
                    try {
                        Thread.sleep(1000)
                    } catch (e: InterruptedException) {
                        return
                    }
                    networkInfo = getConnectedNetworkInfo(context)
                    if (networkInfo != null) break
                }
            }
            if (!isSameNetworkType(this@AndroidRouter.networkInfo, networkInfo)) {
                this@AndroidRouter.networkInfo = networkInfo
            }
        }

        protected fun isSameNetworkType(network1: NetworkInfo?, network2: NetworkInfo?): Boolean {
            return network1?.type == network2?.type
        }

        protected fun displayIntentInfo(intent: Intent) {

        }
    }
}