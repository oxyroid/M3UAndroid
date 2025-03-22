package com.m3u.smartphone.ui.common.internal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.m3u.core.wrapper.Event
import com.m3u.core.wrapper.handledEvent
import com.m3u.smartphone.ui.material.components.SettingDestination

object Events {
    var settingDestination: Event<SettingDestination> by mutableStateOf(handledEvent())
    var discoverCategory: Event<String> by mutableStateOf(handledEvent())
}