package com.m3u.data.service

import com.m3u.core.wrapper.Message
import kotlinx.coroutines.flow.StateFlow

interface DynamicMessageService {
    fun emit(message: Message.Dynamic)
    val message: StateFlow<Message.Dynamic>
}
