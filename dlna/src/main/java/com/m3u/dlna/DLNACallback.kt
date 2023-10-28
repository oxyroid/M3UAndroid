package com.m3u.dlna

import org.fourthline.cling.model.meta.Device

/**
 * this listener call in UI thread.
 */
interface OnDeviceRegistryListener {
    fun onDeviceAdded(device: Device<*, *, *>) {}
    fun onDeviceRemoved(device: Device<*, *, *>) {}
}
