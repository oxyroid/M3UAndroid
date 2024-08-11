package com.m3u.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.m3u.core.wrapper.Event
import com.m3u.core.wrapper.eventOf
import com.m3u.core.wrapper.handledEvent
import com.m3u.data.service.DPadReactionService
import com.m3u.data.tv.model.RemoteDirection
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

object Events {
    var settingDestination: Event<SettingDestination> by mutableStateOf(handledEvent())
    var discoverCategory: Event<String> by mutableStateOf(handledEvent())
    var remoteDirection: Event<RemoteDirection> by mutableStateOf(handledEvent())

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface EventBusEntryPoint {
        val dPadReactionService: DPadReactionService
    }

    fun ComponentActivity.enableDPadReaction() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                EntryPointAccessors
                    .fromApplication<EventBusEntryPoint>(applicationContext)
                    .dPadReactionService
                    .incoming
                    .onEach { remoteDirection = eventOf(it) }
                    .launchIn(this)
            }
        }
    }
}