package com.m3u.data.service.impl

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
    override fun snack(message: String) {
        MainScope().launch {
            _snacker.notify(message)
        }
    }

    override fun toast(message: String) {
        MainScope().launch {
            _toaster.notify(message)
        }
    }

    private val _snacker = MutableStateFlow("")
    override val snacker: StateFlow<String> get() = _snacker.asStateFlow()

    private val _toaster = MutableStateFlow("")
    override val toaster: StateFlow<String> get() = _toaster.asStateFlow()

    private var job: Job? = null
    private suspend fun MutableStateFlow<String>.notify(
        value: String,
        duration: Duration = 3.seconds
    ) {
        job?.cancel()
        job = coroutineScope {
            launch {
                this@notify.value = value
                delay(duration)
            }.apply {
                invokeOnCompletion {
                    this@notify.value = ""
                }
            }
        }
    }
}
