package com.m3u.data.service

import com.m3u.core.wrapper.Message
import kotlinx.coroutines.flow.StateFlow

interface Messager {
    fun emit(message: Message)
    fun emit(message: String)
    val message: StateFlow<Message>
}
