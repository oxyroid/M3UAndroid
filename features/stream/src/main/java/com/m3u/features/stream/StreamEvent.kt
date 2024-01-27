package com.m3u.features.stream

import android.content.Context
import org.jupnp.model.meta.Device

sealed interface StreamEvent {
    data class OpenDlnaDevices(val activityContext: Context) : StreamEvent
    data class CloseDlnaDevices(val activityContext: Context) : StreamEvent
    data class ConnectDlnaDevice(val device: Device<*, *, *>) : StreamEvent
    data class DisconnectDlnaDevice(val device: Device<*, *, *>) : StreamEvent
    data object Release : StreamEvent
    data class OnVolume(val volume: Float) : StreamEvent
    data class OnFavourite(val url: String) : StreamEvent
}