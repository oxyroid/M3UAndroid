package com.m3u.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.m3u.core.wrapper.Event
import com.m3u.core.wrapper.handledEvent

object EventBus {
    var settings: Event<Settings> by mutableStateOf(handledEvent())
    var discoverCategory: Event<String> by mutableStateOf(handledEvent())
}