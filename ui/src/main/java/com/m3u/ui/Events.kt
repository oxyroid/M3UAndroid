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
import com.m3u.data.television.model.RemoteDirection
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

object Events {
    var settingDestination: Event<SettingDestination> by mutableStateOf(handledEvent())
    var discoverCategory: Event<String> by mutableStateOf(handledEvent())
    var remoteDirection: Event<RemoteDirection> by mutableStateOf(handledEvent())

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface EventBusEntryPoint {
        val remoteDirectionService: RemoteDirectionService
    }

    fun ComponentActivity.connectDPadIntent() {
        EntryPointAccessors
            .fromApplication<EventBusEntryPoint>(applicationContext)
            .remoteDirectionService
            .incoming
            .flowWithLifecycle(lifecycle)
            .onEach { remoteDirection = eventOf(it) }
            .launchIn(lifecycleScope)
    }
}