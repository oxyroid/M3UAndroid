package com.m3u.data.repository

import android.net.nsd.NsdServiceInfo
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.SharedFlow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

interface TvRepository {
    val fromPhone: SharedFlow<String>
    val broadcast: SharedFlow<String>
    val pairServerState: SharedFlow<PairServerState>
    val pairClientState: SharedFlow<PairClientState>

    // You can not stop server.
    suspend fun startServer()
    suspend fun startClient()
    fun stopClient()

    suspend fun toTelevision(body: String): String
    suspend fun pair(pin: Int, timeout: Duration = 8.seconds)

    fun release()
}

@Immutable
sealed interface PairServerState {
    @Immutable
    data object Idle : PairServerState
    @Immutable
    data class Prepared(val pin: Int) : PairServerState
    @Immutable
    data class Connected(val info: NsdServiceInfo) : PairServerState
}

@Immutable
sealed interface PairClientState {
    @Immutable
    data object Idle : PairClientState
    @Immutable
    data object Connecting: PairClientState
    @Immutable
    data class Connected(val info: NsdServiceInfo) : PairClientState
}