package com.m3u.data.service.internal

import com.m3u.core.architecture.dispatcher.Dispatcher
import com.m3u.core.architecture.dispatcher.M3uDispatchers.IO
import com.m3u.core.wrapper.Message
import com.m3u.data.service.Messager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class MessagerImpl @Inject constructor(
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher
) : Messager {
    private val _message: MutableStateFlow<Message> = MutableStateFlow(Message.Dynamic.EMPTY)
    override val message: StateFlow<Message> get() = _message.asStateFlow()

    private var job: Job? = null
    private val coroutineScope = CoroutineScope(ioDispatcher)

    override fun emit(message: Message) {
        job?.cancel()
        job = coroutineScope.launch {
            _message.value = message
            delay(message.duration)
            _message.value = Message.Dynamic.EMPTY
        }
    }

    override fun emit(message: String) {
        emit(Message.Dynamic(message, Message.LEVEL_WARN, "", Message.TYPE_SNACK))
    }
}