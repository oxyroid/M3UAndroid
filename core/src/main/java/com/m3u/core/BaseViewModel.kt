package com.m3u.core

import android.os.Parcelable
import androidx.annotation.CallSuper
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

abstract class BaseViewModel<S : Parcelable, E>(
    emptyState: S,
    private val savedStateHandle: SavedStateHandle,
    private val key: String
) : ViewModel() {

    protected val writable: MutableStateFlow<S> =
        MutableStateFlow(savedStateHandle[key] ?: emptyState)

    val readable: StateFlow<S> = writable.asStateFlow()

    abstract fun onEvent(event: E)

    @CallSuper
    override fun onCleared() {
        super.onCleared()
        savedStateHandle[key] = readable.value
    }

}