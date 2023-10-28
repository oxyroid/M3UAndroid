package com.m3u.core.architecture.viewmodel

import android.app.Application
import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * MVI architecture ViewModel.
 * 1. The `readable` and `writable` fields should be used in ViewModels themself.
 * 2. ViewModel should change writable value to update current state.
 * 3. It also provides context but not making memory lacking.
 */
abstract class BaseViewModel<S, in E>(
    application: Application,
    emptyState: S
) : AndroidViewModel(application) {
    protected val writable: MutableStateFlow<S> = MutableStateFlow(emptyState)
    protected val readable: S get() = writable.value
    val state: StateFlow<S> = writable.asStateFlow()
    abstract fun onEvent(event: E)
    protected val cacheDir: File get() = context.cacheDir

    private val context: Context get() = getApplication()

    protected fun string(@StringRes resId: Int): String = context.getString(resId)
    protected fun string(@StringRes resId: Int, vararg formatArgs: Any?): String =
        context.getString(resId, formatArgs)
}
