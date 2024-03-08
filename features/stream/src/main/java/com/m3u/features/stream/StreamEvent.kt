package com.m3u.features.stream

import org.jupnp.model.meta.Device

sealed interface StreamEvent {
    data object OpenDlnaDevices : StreamEvent
    data object CloseDlnaDevices : StreamEvent
    data class ConnectDlnaDevice(val device: Device<*, *, *>) : StreamEvent
    data class DisconnectDlnaDevice(val device: Device<*, *, *>) : StreamEvent
    data class OnVolume(val volume: Float) : StreamEvent
    data class OnFavourite(val streamId: Int) : StreamEvent
}