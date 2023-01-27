package com.m3u.features.setting

sealed interface SettingEvent {
    data class OnTitle(val title: String) : SettingEvent

    data class OnUrl(val url: String) : SettingEvent

    object SubscribeUrl: SettingEvent
}