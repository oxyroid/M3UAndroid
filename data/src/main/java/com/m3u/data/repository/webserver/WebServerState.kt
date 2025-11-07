package com.m3u.data.repository.webserver

import androidx.compose.runtime.Immutable

@Immutable
data class WebServerState(
    val isRunning: Boolean = false,
    val ipAddress: String? = null,
    val port: Int = 8080,
    val error: String? = null
) {
    val accessUrl: String?
        get() = if (isRunning && ipAddress != null) {
            "http://$ipAddress:$port"
        } else null
}
