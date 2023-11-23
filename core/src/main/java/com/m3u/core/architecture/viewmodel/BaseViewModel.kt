package com.m3u.core.architecture.viewmodel

import androidx.lifecycle.ViewModel
import com.m3u.core.wrapper.Circuit
import com.m3u.core.wrapper.Event
import com.m3u.core.wrapper.Message
import com.m3u.core.wrapper.Percent
import com.m3u.core.wrapper.Process
import com.m3u.core.wrapper.Resource
import com.m3u.core.wrapper.eventOf
import com.m3u.core.wrapper.handledEvent
import com.m3u.core.wrapper.pt
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

abstract class BaseViewModel<S, in E, M : Message>(
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

fun <T> Resource<T>.circuit(): Circuit<T> {
    return when (this) {
        is Resource.Success -> Circuit(data = { data })
        is Resource.Failure -> Circuit(message = { message })
        else -> Circuit()
    }
}

fun <T> Process<T>.circuit(): Circuit<T> {
    return when (this) {
        is Process.Success -> Circuit(data = { data })
        is Process.Failure -> Circuit(message = { message })
        is Process.Loading -> Circuit()
    }
}

fun Process<*>.percentCircuit(): Circuit<Percent> {
    return when (this) {
        is Process.Success -> Circuit(data = { 100.pt })
        is Process.Failure -> Circuit(message = { message })
        is Process.Loading -> Circuit(data = { value })
    }
}

fun <T, R> Circuit<T>.map(transform: T.() -> R): Circuit<R> {
    val from = getData()
    val to = from?.let(transform)
    return Circuit(data = { to }, message)
}

fun <T> Circuit<T>.onEach(block: (T) -> Unit): Circuit<T> {
    getData()?.let(block)
    return this
}

fun <T> Circuit<T>.catch(block: (String) -> Unit): Circuit<T> {
    getMessage()?.let(block)
    return this
}
