package com.m3u.features.live

sealed interface LiveEvent {
    data class Init(val liveId: Int) : LiveEvent
    object SearchDlnaDevices : LiveEvent
    object EnterPipMode : LiveEvent
}