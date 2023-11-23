package com.m3u.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.m3u.core.wrapper.Event
import com.m3u.core.wrapper.Message

@Composable
fun MessageEventHandler(
    message: Event<Message>,
    helper: Helper = LocalHelper.current
) {
    val context = LocalContext.current
    EventHandler(message) {
        context
            .getString(it.resId, it.formatArgs)
            .let(helper::snake)
    }
}