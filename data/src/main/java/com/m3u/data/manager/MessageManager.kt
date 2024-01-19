package com.m3u.data.manager

import com.m3u.core.wrapper.Message
import kotlinx.coroutines.flow.StateFlow

interface MessageManager {
    fun emit(message: Message)
    val message: StateFlow<Message>
}
