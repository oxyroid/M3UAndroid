package com.m3u.data.service

import com.m3u.core.architecture.service.BannerService
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class ConflatedBannerService @Inject constructor() : BannerService {
    override fun append(message: String) {
        MainScope().launch {
            _messages.emit(message)
        }
    }

    private val _messages = MutableSharedFlow<String>()
    override val messages: SharedFlow<String> get() = _messages.asSharedFlow()
}