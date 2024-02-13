package com.m3u.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

abstract class TvRepository {
    abstract val pinCodeForServer: StateFlow<Int?>
    protected abstract fun startForServer()
    protected abstract fun stopForServer()

    abstract fun pairForClient(pin: Int, timeout: Duration = 8.seconds): Flow<PairState>
}

sealed interface PairState {
    data object Idle : PairState
    data object Timeout : PairState
    data object Connecting : PairState
    data class Connected(val host: String, val port: Int) : PairState
}