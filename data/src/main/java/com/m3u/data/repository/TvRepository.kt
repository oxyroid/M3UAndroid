package com.m3u.data.repository

import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

interface TvRepository {
    /**
     * @return PIN code
     */
    fun startServer(): Flow<Int?>

    /**
     * @return address
     */
    fun pair(pin: Int, timeout: Duration = 8.seconds): Flow<PairState>
    fun release()
}

sealed interface PairState {
    data object Idle : PairState
    data object Timeout : PairState
    data object Connecting : PairState
    data class Connected(val host: String, val port: Int) : PairState
}