package com.m3u.data.repository.television

import android.net.Uri
import com.m3u.core.architecture.Abi
import com.m3u.data.television.model.Television
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

abstract class TelevisionRepository {
    abstract val broadcastCodeOnTelevision: StateFlow<Int?>

    protected abstract fun broadcastOnTelevision()
    protected abstract fun closeBroadcastOnTelevision()

    abstract val connected: StateFlow<Television?>
    abstract val allUpdateStates: StateFlow<Map<UpdateKey, UpdateState>>

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

data class UpdateKey(
    val version: Int,
    val abi: Abi
)

sealed class UpdateState {
    data object Idle : UpdateState()
    data class Prepared(val url: String) : UpdateState()
    data class Downloading(val count: Long) : UpdateState()
    data class Downloaded(val uri: Uri) : UpdateState()
    data object Installing : UpdateState()
    data class Failed(val reason: String)
}