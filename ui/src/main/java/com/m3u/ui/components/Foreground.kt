package com.m3u.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

typealias ForegroundContent = @Composable () -> Unit
typealias ForegroundDismiss = () -> Unit

data class Foreground(
    val content: ForegroundContent,
    val dismiss: ForegroundDismiss
)

@Composable
fun Foreground(
    visible: Boolean = true,
    dismiss: ForegroundDismiss = {},
    content: ForegroundContent,
) {
    DisposableEffect(dismiss, content, visible) {
        foreground = if (visible) Foreground(content, dismiss) else null
        onDispose {
            foreground = null
        }
    }
}

private var foreground by mutableStateOf<Foreground?>(null)

@Composable
fun ForegroundHost() {
    AppDialog(
        visible = foreground != null,
        onDismiss = foreground?.dismiss ?: {}
    ) {
        foreground?.content?.invoke()
    }
}
