package com.m3u.features.setting

import com.m3u.core.annotation.FeedStrategy

sealed interface SettingEvent {
    data class OnTitle(val title: String) : SettingEvent
    data class OnUrl(val url: String) : SettingEvent
    data class OnSyncMode(@FeedStrategy val feedStrategy: Int) : SettingEvent
    object OnSubscribe : SettingEvent
    object OnUIMode : SettingEvent
    object OnEditMode : SettingEvent
    object OnConnectTimeout : SettingEvent
}