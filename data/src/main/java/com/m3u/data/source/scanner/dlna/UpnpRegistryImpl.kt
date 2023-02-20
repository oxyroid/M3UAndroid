package com.m3u.data.source.scanner.dlna

import com.m3u.data.source.scanner.UpnpRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.fourthline.cling.UpnpService
import org.fourthline.cling.UpnpServiceImpl
import org.fourthline.cling.android.AndroidUpnpServiceConfiguration
import org.fourthline.cling.binding.xml.DeviceDescriptorBinder
import org.fourthline.cling.binding.xml.ServiceDescriptorBinder
import org.fourthline.cling.binding.xml.UDA10DeviceDescriptorBinderImpl
import org.fourthline.cling.binding.xml.UDA10ServiceDescriptorBinderImpl
import org.fourthline.cling.model.message.header.STAllHeader
import org.fourthline.cling.model.meta.LocalDevice
import org.fourthline.cling.model.meta.RemoteDevice
import org.fourthline.cling.registry.Registry
import org.fourthline.cling.registry.RegistryListener

class UpnpRegistryImpl : UpnpRegistry {
    private val remoteDevices = mutableListOf<RemoteDevice>()
    private val localDevices = mutableListOf<LocalDevice>()
    private val listener = object : RegistryListener {
        override fun remoteDeviceDiscoveryStarted(registry: Registry, device: RemoteDevice) {}

        override fun remoteDeviceDiscoveryFailed(
            registry: Registry,
            device: RemoteDevice,
            ex: Exception
        ) {
        }

        override fun remoteDeviceAdded(registry: Registry, device: RemoteDevice) {
            remoteDevices.add(device)
        }

        override fun remoteDeviceUpdated(registry: Registry, device: RemoteDevice) {
            // remoteDevices.add(device)
        }

        override fun remoteDeviceRemoved(registry: Registry, device: RemoteDevice) {
            remoteDevices.remove(device)
        }

        override fun localDeviceAdded(registry: Registry, device: LocalDevice) {
            localDevices.add(device)
        }

        override fun localDeviceRemoved(registry: Registry, device: LocalDevice) {
            localDevices.remove(device)
        }

        override fun beforeShutdown(registry: Registry) {}

        override fun afterShutdown() {}
    }
    private var service: UpnpService? = null

    override suspend fun remoteDevices(): List<RemoteDevice> = withContext(Dispatchers.IO) {
        try {
            init()
            remoteDevices.clear()
            service?.apply {
                controlPoint.search(STAllHeader())
                delay(3000)
                shutdown()
            }
            remoteDevices
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun localDevices(): List<LocalDevice> = withContext(Dispatchers.IO) {
        try {
            init()
            localDevices.clear()
            service?.apply {
                controlPoint.search(STAllHeader())
                delay(3000)
                shutdown()
            }
            localDevices
        } catch (e: java.lang.Exception) {
            emptyList()
        }
    }

    private fun init() {
        service = UpnpServiceImpl(
            object : AndroidUpnpServiceConfiguration() {
                override fun getDeviceDescriptorBinderUDA10(): DeviceDescriptorBinder {
                    return UDA10DeviceDescriptorBinderImpl()
                }

                override fun getServiceDescriptorBinderUDA10(): ServiceDescriptorBinder {
                    return UDA10ServiceDescriptorBinderImpl()
                }
            },
            listener
        )
    }
}