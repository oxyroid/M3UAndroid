package com.m3u.features.setting

sealed interface SettingEvent {
    object OnSubscribe : SettingEvent
    data class OnTitle(val title: String) : SettingEvent
    data class OnUrl(val url: String) : SettingEvent
    object OnSyncMode : SettingEvent
    object OnUseCommonUIMode : SettingEvent
    object OnExperimentalMode : SettingEvent
    object OnConnectTimeout : SettingEvent
    object OnClipMode : SettingEvent
    data class OnBannedLive(val id: Int) : SettingEvent
    object OnInitialTabIndex : SettingEvent
    object OnSilentMode : SettingEvent
}