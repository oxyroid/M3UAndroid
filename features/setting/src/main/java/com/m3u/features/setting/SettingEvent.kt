package com.m3u.features.setting

import android.net.Uri

sealed interface SettingEvent {
    data object Subscribe : SettingEvent
    data class OnTitle(val title: String) : SettingEvent
    data class OnUrl(val url: String) : SettingEvent
    data class OpenDocument(val uri: Uri = Uri.EMPTY) : SettingEvent
    data object OnLocalStorage : SettingEvent
}