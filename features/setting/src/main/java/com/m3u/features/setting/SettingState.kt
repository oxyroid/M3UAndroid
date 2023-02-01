package com.m3u.features.setting

import com.m3u.core.annotation.SyncMode
import com.m3u.core.wrapper.Event

data class SettingState(
    val adding: Boolean = false,
    val title: String = "",
    val url: String = "",
    val message: Event<String> = Event.Handled(),
    val version: String = "",
    val syncMode: Int = SyncMode.DEFAULT
)