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

    abstract val connectedTelevision: StateFlow<SayHello.TelevisionInfo?>

    abstract fun connectToTelevision(
        broadcastCode: Int,
        timeout: Duration = 8.seconds
    ): Flow<ConnectionToTelevisionValue>

    abstract suspend fun disconnectToTelevision()
}

sealed interface ConnectionToTelevisionValue {
    data class Idle(val reason: String? = null) : ConnectionToTelevisionValue
    data object Searching : ConnectionToTelevisionValue
    data object Connecting : ConnectionToTelevisionValue
    data object Timeout : ConnectionToTelevisionValue
    data class Completed(val host: String, val port: Int) : ConnectionToTelevisionValue
}