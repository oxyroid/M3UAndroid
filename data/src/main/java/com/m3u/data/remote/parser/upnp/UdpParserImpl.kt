package com.m3u.data.remote.parser.upnp

import android.content.Context
import android.net.wifi.p2p.WifiP2pManager
import android.os.Looper
import com.m3u.core.architecture.logger.Logger
import com.m3u.core.architecture.logger.execute
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class UdpParserImpl @Inject constructor(
    @ApplicationContext context: Context,
    private val logger: Logger
) : UdpParser {
    private val looper = Looper.getMainLooper()
    private val manager = context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
    private val channel: WifiP2pManager.Channel = manager.initialize(context, looper, null)

    override suspend fun execute(input: Unit): List<String> = suspendCancellableCoroutine {
        logger.execute {
            manager.requestPeers(channel) { result ->
                val info = result.deviceList.map {
                    "${it.deviceAddress} ${it.deviceName} ${it.secondaryDeviceType}"
                }
                it.resume(info)
            }
            it.invokeOnCancellation {
                manager.cancelConnect(channel, null)
            }
        }
    }
}