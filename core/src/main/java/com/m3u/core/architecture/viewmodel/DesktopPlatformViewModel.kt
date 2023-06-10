package com.m3u.core.architecture.viewmodel

import kotlinx.coroutines.flow.StateFlow

class DesktopPlatformViewModel<S, in E> : PlatformViewModel<S, E> {
    override val state: StateFlow<S>
        get() = TODO("Not yet implemented")

    override fun onEvent(event: E) {
        TODO("Not yet implemented")
    }
}