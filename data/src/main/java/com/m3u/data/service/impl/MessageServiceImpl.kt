package com.m3u.data.service.impl

import com.m3u.core.wrapper.Message
import com.m3u.data.service.MessageService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

class MessageServiceImpl @Inject constructor() : MessageService {
    private val _message: MutableStateFlow<Message> = MutableStateFlow(Message.Dynamic.EMPTY)
    override val message: StateFlow<Message> get() = _message.asStateFlow()

    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private val job = AtomicReference<Job?>()

    override fun emit(message: Message) {
        job.getAndUpdate { prev ->
            prev?.cancel()
            coroutineScope.launch {
                _message.update { message }
                delay(message.duration)
                _message.update { Message.Dynamic.EMPTY }
            }
        }
    }
}
