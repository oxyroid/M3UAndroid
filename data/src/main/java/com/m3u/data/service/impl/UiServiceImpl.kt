package com.m3u.data.service.impl

import com.m3u.data.service.Message
import com.m3u.data.service.UiService
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class UiServiceImpl @Inject constructor() : UiService {
    override fun snack(message: Message) {
        MainScope().launch {
            _snacker.notify(message)
        }
    }

    override fun toast(message: Message) {
        MainScope().launch {
            _toaster.notify(message)
        }
    }

    private val _snacker = MutableStateFlow(Message.Empty)
    override val snacker: StateFlow<Message> get() = _snacker.asStateFlow()

    private val _toaster = MutableStateFlow(Message.Empty)
    override val toaster: StateFlow<Message> get() = _toaster.asStateFlow()

    private var job: Job? = null
    private suspend fun MutableStateFlow<Message>.notify(
        value: Message,
        duration: Duration = 3.seconds
    ) = coroutineScope {
        job?.cancel()
        job = launch {
            this@notify.value = value
            delay(duration)
            this@notify.value = Message.Empty
        }
    }
}
