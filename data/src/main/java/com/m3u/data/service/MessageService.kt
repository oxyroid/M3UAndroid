package com.m3u.data.service

import com.m3u.core.wrapper.Message
import kotlinx.coroutines.flow.StateFlow

interface MessageService {
    fun emit(message: Message)
    val message: StateFlow<Message>
}
