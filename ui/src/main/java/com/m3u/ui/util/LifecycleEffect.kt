package com.m3u.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext

@Composable
@NonRestartableComposable
fun LifecycleEffect(
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
    effect: LifecycleOwner.(Lifecycle.Event) -> Unit
) {
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver(effect)
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}

@Composable
@NonRestartableComposable
fun RepeatOnCreate(
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
    block: suspend CoroutineScope.() -> Unit
) {
    RepeatOnLifecycle(
        lifecycleOwner = lifecycleOwner,
        state = Lifecycle.State.CREATED,
        block = block
    )
}

@Composable
@NonRestartableComposable
fun RepeatOnStart(
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
    block: suspend CoroutineScope.() -> Unit
) {
    RepeatOnLifecycle(
        lifecycleOwner = lifecycleOwner,
        state = Lifecycle.State.STARTED,
        block = block
    )
}

@Composable
@NonRestartableComposable
fun RepeatOnResume(
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
    block: suspend CoroutineScope.() -> Unit
) {
    RepeatOnLifecycle(
        lifecycleOwner = lifecycleOwner,
        state = Lifecycle.State.RESUMED,
        block = block
    )
}

@Composable
@NonRestartableComposable
fun RepeatOnLifecycle(
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
    state: Lifecycle.State,
    block: suspend CoroutineScope.() -> Unit
) {
    val currentBlock by rememberUpdatedStateWithLifecycle(
        initialState = block,
        updater = { block }
    )
    LaunchedEffect(lifecycleOwner, state) {
        lifecycleOwner.repeatOnLifecycle(state, currentBlock)
    }
}

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