package com.m3u.ui

import androidx.compose.runtime.Composable
import com.m3u.core.wrapper.Event
import com.m3u.core.wrapper.Message

@Composable
fun MessageEventHandler(
    message: Event<Message.Static>,
    helper: Helper = LocalHelper.current
) {
    EventHandler(message) {
        helper.log(it)
    }
}