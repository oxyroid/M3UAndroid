package com.m3u.data.source.scanner

import org.fourthline.cling.model.meta.LocalDevice
import org.fourthline.cling.model.meta.RemoteDevice

interface UpnpRegistry {
    suspend fun remoteDevices(): List<RemoteDevice>
    suspend fun localDevices(): List<LocalDevice>
}