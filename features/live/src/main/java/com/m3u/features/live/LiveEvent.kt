package com.m3u.features.live

sealed interface LiveEvent {
    sealed interface Init : LiveEvent

    data class InitSpecial(val liveId: Int) : Init
    data class InitPlayList(val initialIndex: Int, val ids: List<Int>) : Init

    object SearchDlnaDevices : LiveEvent
    object Record : LiveEvent
    data class InstallMedia(val url: String) : LiveEvent
    object UninstallMedia : LiveEvent
    object OnMuted : LiveEvent
    data class OnFavourite(val url: String) : LiveEvent
}