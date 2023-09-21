package com.m3u.features.setting

import android.net.Uri

sealed interface SettingEvent {
    data object Subscribe : SettingEvent
    data class OnTitle(val title: String) : SettingEvent
    data class OnUrl(val url: String) : SettingEvent
    data object OnSyncMode : SettingEvent
    data object OnUseCommonUIMode : SettingEvent
    data object OnExperimentalMode : SettingEvent
    data object OnConnectTimeout : SettingEvent
    data object OnClipMode : SettingEvent
    data class OnBannedLive(val id: Int) : SettingEvent
    data class ImportJavaScript(val uri: Uri) : SettingEvent

    data object ScrollDefaultDestination : SettingEvent
    data object OnSilentMode : SettingEvent
}