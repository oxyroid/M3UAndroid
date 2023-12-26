package com.m3u.data.service.impl

import com.m3u.core.wrapper.Message
import com.m3u.data.service.DynamicMessageService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.concurrent.Volatile

class DynamicMessageServiceImpl @Inject constructor() : DynamicMessageService {

    private val _message = MutableStateFlow(Message.Dynamic.EMPTY)
    override val message: StateFlow<Message.Dynamic> get() = _message.asStateFlow()

    @Volatile
    private var job: Job? = null

    override fun emit(message: Message.Dynamic) {
        CoroutineScope(Dispatchers.Main.immediate).launch {
            job?.cancel()
            job = launch {
                _message.update { message }
                delay(message.duration)
                _message.update { Message.Dynamic.EMPTY }
            }
        }
    }
}
