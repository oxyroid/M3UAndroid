package com.m3u.core.architecture.service

import kotlinx.coroutines.flow.SharedFlow

interface UserInterface {
    fun append(message: String)
    val messages: SharedFlow<String>
}