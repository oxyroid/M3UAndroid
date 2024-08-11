package com.m3u.data.service

import androidx.compose.runtime.Immutable
import com.m3u.data.tv.model.RemoteDirection
import kotlinx.coroutines.flow.SharedFlow

@Immutable
interface DPadReactionService {
    val incoming: SharedFlow<RemoteDirection>
    suspend fun emit(remoteDirection: RemoteDirection)
}