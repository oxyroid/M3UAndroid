package com.m3u.core.architecture.viewmodel

import androidx.lifecycle.ViewModel
import com.m3u.core.wrapper.Event
import com.m3u.core.wrapper.eventOf
import com.m3u.core.wrapper.handledEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * MVI architecture ViewModel.
 * 1. The `readable` and `writable` fields should be used in ViewModels themself.
 * 2. ViewModel should change writable value to update current state.
 * 3. It also provides context but not making memory lacking.
 */
abstract class BaseViewModel<S, in E, M>(
    emptyState: S
) : ViewModel() {
    protected val writable: MutableStateFlow<S> = MutableStateFlow(emptyState)
    protected val readable: S get() = writable.value
    val state: StateFlow<S> = writable.asStateFlow()
    abstract fun onEvent(event: E)
    private val _message: MutableStateFlow<Event<M>> = MutableStateFlow(handledEvent())
    val message: StateFlow<Event<M>> = _message.asStateFlow()
    fun onMessage(message: M) {
        _message.update { eventOf(message) }
    }
}
