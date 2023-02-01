package com.m3u.core.architecture

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

abstract class BaseViewModel<S, in E>(
    application: Application,
    emptyState: S
) : AndroidViewModel(application) {
    protected val writable: MutableStateFlow<S> = MutableStateFlow(emptyState)
    protected val readable: S get() = writable.value
    val state: StateFlow<S> = writable.asStateFlow()
    abstract fun onEvent(event: E)

    protected val context: Context get() = getApplication()
}