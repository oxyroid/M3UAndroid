package com.m3u.data.local.service.internal

import com.m3u.core.architecture.dispatcher.Dispatcher
import com.m3u.core.architecture.dispatcher.M3uDispatchers.IO
import com.m3u.core.wrapper.Message
import com.m3u.data.local.service.MessageManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.concurrent.Volatile

class MessageManagerImpl @Inject constructor(
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher
) : MessageManager {
    private val _message: MutableStateFlow<Message> = MutableStateFlow(Message.Dynamic.EMPTY)
    override val message: StateFlow<Message> get() = _message.asStateFlow()

    private var job: Job? = null
    private val coroutineScope = CoroutineScope(ioDispatcher)

    override fun emit(message: Message) {
        job?.cancel()
        job = coroutineScope.launch {
            _message.update { message }
            while (true) {
                delay(message.duration)
                if (!isLocked) break
            }
            _message.update { Message.Dynamic.EMPTY }
        }
    }

    @Volatile
    private var isLocked = false

    override fun lock() {
        isLocked = true
    }

    override fun unlock() {
        isLocked = false
    }
}