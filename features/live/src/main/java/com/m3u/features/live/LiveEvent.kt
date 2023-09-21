package com.m3u.features.live

sealed interface LiveEvent {
    sealed interface Init : LiveEvent

    data class InitSpecial(val liveId: Int) : Init
    data class InitPlayList(val initialIndex: Int, val ids: List<Int>) : Init

    data object SearchDlnaDevices : LiveEvent
    data object Record : LiveEvent
    data class InstallMedia(val url: String) : LiveEvent
    data object UninstallMedia : LiveEvent
    data object OnMuted : LiveEvent
    data class OnFavourite(val url: String) : LiveEvent
}