package com.m3u.data.television.nsd.internal

import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.m3u.core.architecture.dispatcher.Dispatcher
import com.m3u.core.architecture.dispatcher.M3uDispatchers.IO
import com.m3u.core.architecture.logger.Logger
import com.m3u.core.architecture.logger.prefix
import com.m3u.data.television.Utils
import com.m3u.data.television.nsd.NsdDeviceManager
import com.m3u.data.television.nsd.NsdDeviceManager.Companion.META_DATA_PIN
import com.m3u.data.television.nsd.NsdDeviceManager.Companion.SERVICE_TYPE
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

class NsdDeviceManagerImpl @Inject constructor(
    private val nsdManager: NsdManager,
    actualLogger: Logger,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher
) : NsdDeviceManager {
    private val logger = actualLogger.prefix("nsd")
    override fun search(): Flow<List<NsdServiceInfo>> = callbackFlow<List<NsdServiceInfo>> {
        logger.log("search")
        val result = mutableListOf<NsdServiceInfo>()

        val listener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                logger.log("start discovery failed, error code: $errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                logger.log("stop discovery failed, error code: $errorCode")
            }

            override fun onDiscoveryStarted(serviceType: String?) {
                trySendBlocking(emptyList())
                logger.log("discovery started")
            }

            override fun onDiscoveryStopped(serviceType: String?) {
                logger.log("discovery stopped")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType == SERVICE_TYPE) {
                    object : NsdManager.ResolveListener {
                        override fun onResolveFailed(
                            serviceInfo: NsdServiceInfo,
                            errorCode: Int
                        ) {
                            logger.log("resolve service, error code: $errorCode.")
                        }

                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            result += serviceInfo
                            logger.log("service resolved: $serviceInfo")
                            trySendBlocking(result)
                        }

                        override fun onResolutionStopped(serviceInfo: NsdServiceInfo) {
                            result -= serviceInfo
                            logger.log("resolution stopped: $serviceInfo")
                            trySendBlocking(result)
                        }
                    }.also {
                        @Suppress("DEPRECATION")
                        nsdManager.resolveService(
                            serviceInfo,
                            it
                        )
                    }
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType == SERVICE_TYPE) {
                    result -= serviceInfo
                    logger.log("service lost: $serviceInfo")
                    trySendBlocking(result)
                }
            }
        }
        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        awaitClose {
            nsdManager.stopServiceDiscovery(listener)
        }
    }
        .flowOn(ioDispatcher)

    override fun broadcast(
        name: String,
        port: Int,
        pin: Int,
        metadata: Map<String, Any>
    ): Flow<NsdServiceInfo?> = callbackFlow {
        logger.log("broadcast")
        val localPort = Utils.findPort()

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = name
            serviceType = SERVICE_TYPE
            setPort(localPort)
            setAttribute(META_DATA_PIN, pin.toString())
            metadata.forEach { setAttribute(it.key, it.value.toString()) }
        }

        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(i: NsdServiceInfo) {
                trySendBlocking(serviceInfo)
                logger.log("broadcast registered")
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                trySendBlocking(null)
                logger.log("broadcast un-registered")
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                trySendBlocking(null)
                logger.log("registration failed, error code: $errorCode")
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                trySendBlocking(null)
                logger.log("un-registration failed, error code: $errorCode")
            }

        }
        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
        awaitClose {
            nsdManager.unregisterService(listener)
            trySendBlocking(null)
        }
    }
        .flowOn(ioDispatcher)

}