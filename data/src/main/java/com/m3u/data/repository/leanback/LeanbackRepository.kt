package com.m3u.data.repository.leanback

import com.m3u.data.leanback.model.Leanback
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

abstract class LeanbackRepository {
    abstract val broadcastCodeOnLeanback: StateFlow<Int?>

    protected abstract fun broadcastOnLeanback()
    protected abstract fun closeBroadcastOnLeanback()

    abstract val connected: StateFlow<Leanback?>

    abstract fun connectToLeanback(
        broadcastCode: Int,
        timeout: Duration = 8.seconds
    ): Flow<ConnectionToLeanbackValue>

    abstract suspend fun disconnectToLeanback()
}

sealed interface ConnectionToLeanbackValue {
    data class Idle(val reason: String? = null) : ConnectionToLeanbackValue
    data object Searching : ConnectionToLeanbackValue
    data object Connecting : ConnectionToLeanbackValue
    data object Timeout : ConnectionToLeanbackValue
    data class Completed(val host: String, val port: Int) : ConnectionToLeanbackValue
}
