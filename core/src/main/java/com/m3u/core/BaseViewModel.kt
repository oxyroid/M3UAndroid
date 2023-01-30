package com.m3u.core

import android.app.Application
import android.content.Context
import android.os.Parcelable
import androidx.annotation.CallSuper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

abstract class BaseViewModel<S : Parcelable, E>(
    application: Application,
    emptyState: S,
    private val savedStateHandle: SavedStateHandle,
    private val key: String
) : AndroidViewModel(application) {
    protected val writable: MutableStateFlow<S> =
        MutableStateFlow(savedStateHandle[key] ?: emptyState)

    val state: StateFlow<S> = writable.asStateFlow()
    protected val readable: S get() = state.value

    abstract fun onEvent(event: E)

    @CallSuper
    override fun onCleared() {
        super.onCleared()
        savedStateHandle[key] = state.value
    }

    protected val context: Context get() = getApplication()
}