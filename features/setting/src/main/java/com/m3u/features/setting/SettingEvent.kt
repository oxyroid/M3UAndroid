package com.m3u.features.setting

import com.m3u.core.annotation.SyncMode

sealed interface SettingEvent {
    data class OnTitle(val title: String) : SettingEvent
    data class OnUrl(val url: String) : SettingEvent
    data class OnSyncMode(@SyncMode val syncMode: Int) : SettingEvent
    object OnUIMode : SettingEvent
    object OnSubscribe : SettingEvent
}