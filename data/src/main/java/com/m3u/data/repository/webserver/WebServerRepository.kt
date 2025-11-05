package com.m3u.data.repository.webserver

import kotlinx.coroutines.flow.StateFlow

interface WebServerRepository {
    val state: StateFlow<WebServerState>

    suspend fun start(port: Int = 8080): Result<Unit>
    suspend fun stop(): Result<Unit>
    fun isRunning(): Boolean
}
