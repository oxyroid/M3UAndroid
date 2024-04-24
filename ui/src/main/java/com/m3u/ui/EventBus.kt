package com.m3u.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.m3u.core.wrapper.Event
import com.m3u.core.wrapper.eventOf
import com.m3u.core.wrapper.handledEvent
import com.m3u.data.service.RemoteDirectionService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

object EventBus {
    var settingFragment: Event<SettingFragment> by mutableStateOf(handledEvent())
    var discoverCategory: Event<String> by mutableStateOf(handledEvent())
    var action: Event<RemoteDirectionService.Action> by mutableStateOf(handledEvent())

    fun ComponentActivity.registerActionEventCollector(source: Flow<RemoteDirectionService.Action>) {
        source
            .flowWithLifecycle(lifecycle)
            .onEach { action = eventOf(it) }
            .launchIn(lifecycleScope)
    }
}