package com.m3u.data.repository.tv

import com.m3u.data.tv.model.TvInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

abstract class TvRepository {
    abstract val broadcastCodeOnTv: StateFlow<Int?>

    protected abstract fun broadcastOnTv()
    protected abstract fun closeBroadcastOnTv()

    abstract val connected: StateFlow<TvInfo?>

    abstract fun connectToTv(
        broadcastCode: Int,
        timeout: Duration = 8.seconds
    ): Flow<ConnectionToTvValue>

    abstract suspend fun disconnectToTv()
}

sealed interface ConnectionToTvValue {
    data class Idle(val reason: String? = null) : ConnectionToTvValue
    data object Searching : ConnectionToTvValue
    data object Connecting : ConnectionToTvValue
    data object Timeout : ConnectionToTvValue
    data class Completed(val host: String, val port: Int) : ConnectionToTvValue
}
