package com.m3u.data.service

import kotlinx.coroutines.flow.StateFlow

interface UiService {
    fun snack(message: String)
    fun toast(message: String)
    val snacker: StateFlow<String>
    val toaster: StateFlow<String>
}
