package com.m3u.smartphone.ui.material.ktx

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

@Composable
fun <T> rememberUpdatedStateWithLifecycle(
    initialState: T,
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
    minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
    context: CoroutineContext = EmptyCoroutineContext,
    updater: () -> T
): State<T> = produceState(initialState, minActiveState, context, updater, lifecycleOwner) {
    lifecycleOwner.repeatOnLifecycle(minActiveState) {
        when (context) {
            EmptyCoroutineContext -> this@produceState.value = updater()
            else -> withContext(context) { this@produceState.value = updater() }
        }
    }
}