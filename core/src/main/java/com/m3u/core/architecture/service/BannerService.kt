package com.m3u.core.architecture.service

import kotlinx.coroutines.flow.SharedFlow

interface BannerService {
    fun append(message: String)
    val messages: SharedFlow<String>
}