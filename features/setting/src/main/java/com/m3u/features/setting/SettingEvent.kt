package com.m3u.features.setting

sealed interface SettingEvent {
    data class OnUrlSubmit(val url: String) : SettingEvent
}