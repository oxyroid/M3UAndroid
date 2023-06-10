package com.m3u.core.architecture.viewmodel

import kotlinx.coroutines.flow.StateFlow

/**
 * MVI architecture ViewModel with State and Event Models.
 * 1. State should be collected in view layer.
 * 2. View layer should use onEvent with a special event to notify data layer to answer user`s intent.
 */
interface PlatformViewModel<S, in E> {
    val state: StateFlow<S>
    fun onEvent(event: E)
}