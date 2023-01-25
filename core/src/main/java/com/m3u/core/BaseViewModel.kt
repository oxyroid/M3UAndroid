package com.m3u.core

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

abstract class BaseViewModel<S, E>(emptyState: S) : ViewModel() {
    protected val writable: MutableStateFlow<S> = MutableStateFlow(emptyState)

    val readable: StateFlow<S> = writable.asStateFlow()

    abstract fun onEvent(event: E)

}