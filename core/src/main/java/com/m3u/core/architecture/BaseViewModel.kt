package com.m3u.core.architecture

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * MVI architecture ViewModel with State and Event Models.
 * readable and writable fields should be used in ViewModels themself.
 * state should be collect in view layer.
 * View layer should use onEvent with a special event to notify data layer to answer user`s intent.
 * ViewModel should change writable value to update current state.
 * It also provides context but not making memory lacking.
 */
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