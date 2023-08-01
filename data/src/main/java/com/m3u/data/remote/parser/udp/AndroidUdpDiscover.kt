@file:Suppress("unused")

package com.m3u.data.remote.parser.udp

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.m3u.core.architecture.logger.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import javax.inject.Inject

class AndroidUdpDiscover @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: Logger
) : UdpDiscover {
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

    override fun start(): Flow<String> = flow {
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

    override fun scan(): Flow<List<NsdServiceInfo>> = callbackFlow {
        val list = mutableListOf<NsdServiceInfo>()
        val SERVICE_TYPE = "_dlna._tcp."
        val manager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        val listener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                logger.log("onStartDiscoveryFailed, ServiceType: $serviceType, ErrorCode: $errorCode")
                manager.stopServiceDiscovery(this)
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                logger.log("onStopDiscoveryFailed, ServiceType: $serviceType, ErrorCode: $errorCode")
                manager.stopServiceDiscovery(this)
            }

            override fun onDiscoveryStarted(serviceType: String) {
                logger.log("onDiscoveryStarted, ServiceType: $serviceType")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                logger.log("onDiscoveryStopped, ServiceType: $serviceType")
                close()
            }

            override fun onServiceFound(info: NsdServiceInfo) {
                logger.log("onServiceFound, $info")

                list.add(info)
//                trySendBlocking(list)
            }

            override fun onServiceLost(info: NsdServiceInfo) {
                logger.log("onServiceLost, $info")
                list.remove(info)
//                trySendBlocking(list)
            }
        }
        manager.discoverServices(
            SERVICE_TYPE,
            NsdManager.PROTOCOL_DNS_SD,
            listener
        )
        awaitClose {
            manager.stopServiceDiscovery(listener)
        }
    }
}