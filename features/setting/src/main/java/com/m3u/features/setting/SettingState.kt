package com.m3u.features.setting

import com.m3u.core.wrapper.Event

data class SettingState(
    val adding: Boolean = false,
    val message: Event<String> = Event.Handled(),
    val appVersion: String = ""
)