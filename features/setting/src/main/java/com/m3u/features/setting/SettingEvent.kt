package com.m3u.features.setting

import android.net.Uri

sealed interface SettingEvent {
    object Subscribe : SettingEvent
    data class OnTitle(val title: String) : SettingEvent
    data class OnUrl(val url: String) : SettingEvent
    object OnSyncMode : SettingEvent
    object OnUseCommonUIMode : SettingEvent
    object OnExperimentalMode : SettingEvent
    object OnConnectTimeout : SettingEvent
    object OnClipMode : SettingEvent
    data class OnBannedLive(val id: Int) : SettingEvent
    data class ImportJavaScript(val uri: Uri) : SettingEvent

    object OnInitialDestination : SettingEvent
    object OnSilentMode : SettingEvent
}