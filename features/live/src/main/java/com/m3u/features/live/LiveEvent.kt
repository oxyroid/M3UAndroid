package com.m3u.features.live

import org.fourthline.cling.model.meta.Device

sealed interface LiveEvent {
    sealed interface Init : LiveEvent

    data class InitOne(val liveId: Int) : Init
    data class InitPlayList(val initialIndex: Int, val ids: List<Int>) : Init

    data object OpenDlnaDevices : LiveEvent
    data object CloseDlnaDevices : LiveEvent
    data class ConnectDlnaDevice(val device: Device<*, *, *>) : LiveEvent
    data class DisconnectDlnaDevice(val device: Device<*, *, *>) : LiveEvent
    data object Record : LiveEvent
    data class InstallMedia(val url: String) : LiveEvent
    data object UninstallMedia : LiveEvent
    data object OnMuted : LiveEvent
    data class OnFavourite(val url: String) : LiveEvent
}