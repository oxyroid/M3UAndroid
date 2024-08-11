package com.m3u.data.tv.nsd

import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.m3u.core.architecture.dispatcher.Dispatcher
import com.m3u.core.architecture.dispatcher.M3uDispatchers.IO
import com.m3u.core.architecture.logger.Logger
import com.m3u.core.architecture.logger.Profiles
import com.m3u.core.architecture.logger.install
import com.m3u.data.tv.Utils
import com.m3u.data.tv.nsd.NsdDeviceManager.Companion.META_DATA_PIN
import com.m3u.data.tv.nsd.NsdDeviceManager.Companion.SERVICE_TYPE
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

class NsdDeviceManagerImpl @Inject constructor(
    private val nsdManager: NsdManager,
    delegate: Logger,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher
) : NsdDeviceManager {
    private val logger = delegate.install(Profiles.SERVICE_NSD)

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
                    val listener = NsdResolveListener(
                        onResolved = {
                            if (it != null) {
                                result += it
                                logger.log("service resolve succeed: $serviceInfo")
                                trySendBlocking(result)
                            }
                        },
                        onResolveFailed = {
                            logger.log("service resolve failed")
                            cancel()
                        },
                        onResolvedStopped = {
                            result -= it
                            logger.log("service resolve stopped")
                            trySendBlocking(result)
                        },
                        onStopResolutionFailed = {
                            result -= it
                            logger.log("service stop resolve failed")
                            trySendBlocking(result)
                        }
                    )
                    @Suppress("DEPRECATION")
                    nsdManager.resolveService(serviceInfo, listener)
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
                cancel()
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                trySendBlocking(null)
                logger.log("un-registration failed, error code: $errorCode")
                cancel()
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