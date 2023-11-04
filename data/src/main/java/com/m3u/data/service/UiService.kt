package com.m3u.data.service

import kotlinx.coroutines.flow.StateFlow

@JvmInline
value class Message(val value: String) {
    companion object {
        val Empty = Message("")
    }
}

interface UiService {
    fun snack(message: Message)
    fun toast(message: Message)
    val snacker: StateFlow<Message>
    val toaster: StateFlow<Message>
}
