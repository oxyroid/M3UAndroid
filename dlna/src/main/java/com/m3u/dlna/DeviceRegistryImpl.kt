package com.m3u.dlna

import android.os.Handler
import android.os.Looper
import org.fourthline.cling.model.meta.Device
import org.fourthline.cling.registry.DefaultRegistryListener
import org.fourthline.cling.registry.Registry

internal class DeviceRegistryImpl(private val deviceRegistryListener: OnDeviceRegistryListener) : DefaultRegistryListener() {

    private val logger = Logger.create("DeviceRegistry")
    private val handler = Handler(Looper.getMainLooper())

    override fun deviceAdded(registry: Registry, device: Device<*, *, *>) {
        logger.i("deviceAdded: " + parseDeviceInfo(device))
        // logger.i(parseDeviceService(device))
        handler.post { deviceRegistryListener.onDeviceAdded(device) }
    }

    override fun deviceRemoved(registry: Registry, device: Device<*, *, *>) {
        logger.w("deviceRemoved: " + parseDeviceInfo(device))
        handler.post { deviceRegistryListener.onDeviceRemoved(device) }
    }

    private fun parseDeviceInfo(device: Device<*, *, *>): String = "[${device.type.type}]" +
            "[${device.details.friendlyName}]" +
            "[${device.identity.udn.identifierString.split("-").last()}]"
}