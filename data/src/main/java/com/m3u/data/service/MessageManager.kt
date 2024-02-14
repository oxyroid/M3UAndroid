package com.m3u.data.service

import com.m3u.core.wrapper.Message
import kotlinx.coroutines.flow.StateFlow

interface MessageManager {
    fun emit(message: Message)
    fun lock()
    fun unlock()
    val message: StateFlow<Message>
}