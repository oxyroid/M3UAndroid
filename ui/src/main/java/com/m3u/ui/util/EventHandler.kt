package com.m3u.ui.util

import androidx.compose.runtime.*
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