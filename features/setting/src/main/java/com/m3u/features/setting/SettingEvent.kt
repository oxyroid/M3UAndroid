package com.m3u.features.setting

import com.m3u.core.annotation.ClipMode
import com.m3u.core.annotation.FeedStrategy

sealed interface SettingEvent {
    object OnSubscribe : SettingEvent
    object FetchRelease : SettingEvent
    data class OnTitle(val title: String) : SettingEvent
    data class OnUrl(val url: String) : SettingEvent
    data class OnSyncMode(@FeedStrategy val feedStrategy: Int) : SettingEvent
    object OnUseCommonUIMode : SettingEvent
    object OnEditMode : SettingEvent
    object OnExperimentalMode : SettingEvent
    object OnConnectTimeout : SettingEvent
    object OnScrollMode : SettingEvent
    data class OnClipMode(@ClipMode val mode: Int) : SettingEvent
    data class OnVoiceLiveUrl(val url: String) : SettingEvent
}