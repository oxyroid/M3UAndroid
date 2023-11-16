package com.m3u.features.setting

sealed class SettingMessage {
    data object EmptyTitle : SettingMessage()
    data object EmptyUrl : SettingMessage()
    data object EmptyFile : SettingMessage()
    data object Enqueued : SettingMessage()
}