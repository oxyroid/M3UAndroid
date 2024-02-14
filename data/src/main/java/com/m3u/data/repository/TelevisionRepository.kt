package com.m3u.data.repository

import com.m3u.data.television.http.endpoint.SayHello
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

abstract class TelevisionRepository {
    abstract val broadcastCodeOnTelevision: StateFlow<Int?>

    protected abstract fun broadcastOnTelevision()
    protected abstract fun closeBroadcastOnTelevision()

    abstract val connectedTelevision: StateFlow<SayHello.Rep?>

    abstract fun connectToTelevision(code: Int, timeout: Duration = 8.seconds): Flow<ConnectionToTelevision>
    abstract suspend fun disconnectToTelevision()
}

sealed interface ConnectionToTelevision {
    data class Idle(val reason: String? = null) : ConnectionToTelevision
    data object Timeout : ConnectionToTelevision
    data object Searching: ConnectionToTelevision
    data class Completed(val host: String, val port: Int) : ConnectionToTelevision
}