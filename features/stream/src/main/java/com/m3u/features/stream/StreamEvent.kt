package com.m3u.features.stream

import org.fourthline.cling.model.meta.Device

sealed interface StreamEvent {
    data object OpenDlnaDevices : StreamEvent
    data object CloseDlnaDevices : StreamEvent
    data class ConnectDlnaDevice(val device: Device<*, *, *>) : StreamEvent
    data class DisconnectDlnaDevice(val device: Device<*, *, *>) : StreamEvent
    data object Record : StreamEvent
    data object Stop : StreamEvent
    data class OnVolume(val volume: Float) : StreamEvent
    data class OnFavourite(val url: String) : StreamEvent
}