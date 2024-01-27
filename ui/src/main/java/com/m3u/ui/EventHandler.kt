package com.m3u.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.m3u.core.wrapper.Event
import kotlinx.coroutines.CoroutineScope

@Composable
@NonRestartableComposable
fun <T> EventHandler(
    event: Event<T>,
    handler: suspend CoroutineScope.(T) -> Unit
) {
    val currentHandler by rememberUpdatedState(handler)
    LaunchedEffect(event) {
        event.handle {
            currentHandler(this, it)
        }
    }
}
