package com.m3u.dlna

import org.jupnp.model.meta.Device

/**
 * this listener call in UI thread.
 */
interface OnDeviceRegistryListener {
    fun onDeviceAdded(device: Device<*, *, *>) {}
    fun onDeviceRemoved(device: Device<*, *, *>) {}
}
