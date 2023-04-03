package com.m3u.features.live

sealed interface LiveEvent {
    sealed interface Init : LiveEvent {
        data class SingleLive(val liveId: Int) : Init
        data class PlayList(val initialIndex: Int, val ids: List<Int>) : Init
    }

    object SearchDlnaDevices : LiveEvent
    object Record : LiveEvent
}