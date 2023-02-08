package com.m3u.features.setting

import com.m3u.core.annotation.SyncMode
import com.m3u.core.wrapper.Event
import com.m3u.core.wrapper.handledEvent

data class SettingState(
    val adding: Boolean = false,
    val title: String = "",
    val url: String = "",
    val message: Event<String> = handledEvent(),
    val version: String = "",
    val syncMode: Int = SyncMode.DEFAULT,
    val useCommonUIMode: Boolean = false
)