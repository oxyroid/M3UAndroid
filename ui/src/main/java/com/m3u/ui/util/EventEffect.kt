package com.m3u.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.NonRestartableComposable
import com.m3u.core.wrapper.Event
import kotlinx.coroutines.CoroutineScope

@Composable
@NonRestartableComposable
fun <T> EventEffect(
    event: Event<T>,
    handler: suspend CoroutineScope.(T) -> Unit
) {
    LaunchedEffect(event) {
        event.handle {
            handler(this, it)
        }
    }
}