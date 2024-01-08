package com.m3u.dlna

import android.os.Handler
import android.os.Looper
import org.jupnp.model.meta.Device
import org.jupnp.registry.DefaultRegistryListener
import org.jupnp.registry.Registry

internal class DeviceRegistryImpl(
    private val deviceRegistryListener: OnDeviceRegistryListener
) : DefaultRegistryListener() {

    private val handler = Handler(Looper.getMainLooper())

    override fun deviceAdded(registry: Registry, device: Device<*, *, *>) {
        handler.post { deviceRegistryListener.onDeviceAdded(device) }
    }

    override fun deviceRemoved(registry: Registry, device: Device<*, *, *>) {
        handler.post { deviceRegistryListener.onDeviceRemoved(device) }
    }
}