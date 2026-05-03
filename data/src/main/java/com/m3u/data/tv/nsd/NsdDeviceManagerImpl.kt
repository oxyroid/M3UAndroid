package com.m3u.data.tv.nsd

import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import com.m3u.data.tv.nsd.NsdDeviceManager.Companion.META_DATA_PIN
import com.m3u.data.tv.nsd.NsdDeviceManager.Companion.SERVICE_TYPE
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import timber.log.Timber
import javax.inject.Inject

class NsdDeviceManagerImpl @Inject constructor(
    private val nsdManager: NsdManager,
    private val wifiManager: WifiManager
) : NsdDeviceManager {
    private val timber = Timber.tag("NsdDeviceManager")

    override fun search(): Flow<List<NsdServiceInfo>> = callbackFlow {
        timber.d("search start, serviceType=$SERVICE_TYPE")
        val multicastLock = acquireMulticastLock("m3u-nsd-search")
        val resolvedServices = mutableListOf<NsdServiceInfo>()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                timber.e("discovery start failed, serviceType=$serviceType, errorCode=$errorCode")
                cancel("NSD discovery failed: $errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) = Unit

            override fun onDiscoveryStarted(serviceType: String?) {
                timber.d("discovery started, serviceType=$serviceType")
                trySend(emptyList())
            }

            override fun onDiscoveryStopped(serviceType: String?) {
                timber.d("discovery stopped, serviceType=$serviceType")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                timber.d("service found, name=${serviceInfo.serviceName}, type=${serviceInfo.serviceType}")
                if (serviceInfo.serviceType != SERVICE_TYPE) return
                val resolveListener = NsdResolveListener(
                    onResolved = { resolved ->
                        timber.d(
                            "service resolved, name=${resolved?.serviceName}, host=${resolved?.host}, port=${resolved?.port}, attributes=${resolved?.attributes?.keys}"
                        )
                        if (resolved != null && resolved !in resolvedServices) {
                            resolvedServices += resolved
                            trySend(resolvedServices.toList())
                        }
                    },
                    onResolveFailed = {
                        timber.e("service resolve failed, name=${serviceInfo.serviceName}")
                        cancel("NSD resolve failed")
                    },
                    onResolvedStopped = { resolved ->
                        timber.d("service resolution stopped, name=${resolved.serviceName}")
                        resolvedServices -= resolved
                        trySend(resolvedServices.toList())
                    },
                    onStopResolutionFailed = { resolved ->
                        timber.e("service stop resolution failed, name=${resolved.serviceName}")
                        resolvedServices -= resolved
                        trySend(resolvedServices.toList())
                    }
                )
                @Suppress("DEPRECATION")
                nsdManager.resolveService(serviceInfo, resolveListener)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                timber.d("service lost, name=${serviceInfo.serviceName}, type=${serviceInfo.serviceType}")
                if (serviceInfo.serviceType == SERVICE_TYPE) {
                    resolvedServices.removeAll { it.serviceName == serviceInfo.serviceName }
                    trySend(resolvedServices.toList())
                }
            }
        }

        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        awaitClose {
            timber.d("search close")
            runCatching { nsdManager.stopServiceDiscovery(listener) }
            multicastLock?.releaseSafely("m3u-nsd-search")
        }
    }

    override fun broadcast(
        name: String,
        port: Int,
        pin: Int,
        metadata: Map<String, Any>
    ): Flow<NsdServiceInfo?> = callbackFlow {
        timber.d("broadcast start, name=$name, port=$port, pin=$pin, metadata=$metadata")
        val multicastLock = acquireMulticastLock("m3u-nsd-broadcast")
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = name
            serviceType = SERVICE_TYPE
            setPort(port)
            setAttribute(META_DATA_PIN, pin.toString())
            metadata.forEach { (key, value) -> setAttribute(key, value.toString()) }
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                timber.d("service registered, name=${serviceInfo.serviceName}, port=${serviceInfo.port}")
                trySend(serviceInfo)
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                timber.d("service unregistered, name=${serviceInfo.serviceName}")
                trySend(null)
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                timber.e("registration failed, name=${serviceInfo.serviceName}, errorCode=$errorCode")
                trySend(null)
                cancel("NSD registration failed: $errorCode")
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                timber.e("unregistration failed, name=${serviceInfo.serviceName}, errorCode=$errorCode")
                trySend(null)
                cancel("NSD unregistration failed: $errorCode")
            }
        }

        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
        awaitClose {
            timber.d("broadcast close")
            runCatching { nsdManager.unregisterService(listener) }
            multicastLock?.releaseSafely("m3u-nsd-broadcast")
        }
    }

    private fun acquireMulticastLock(tag: String): WifiManager.MulticastLock? {
        return runCatching {
            wifiManager.createMulticastLock(tag).apply {
                setReferenceCounted(false)
                acquire()
            }
        }.onSuccess {
            timber.d("multicast lock acquired, tag=$tag")
        }.onFailure { error ->
            timber.w(error, "failed to acquire multicast lock, tag=$tag")
        }.getOrNull()
    }

    private fun WifiManager.MulticastLock.releaseSafely(tag: String) {
        runCatching {
            if (isHeld) release()
        }.onSuccess {
            timber.d("multicast lock released, tag=$tag")
        }.onFailure { error ->
            timber.w(error, "failed to release multicast lock, tag=$tag")
        }
    }
}
