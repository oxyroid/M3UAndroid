package com.m3u.data.service

import androidx.compose.runtime.Immutable
import com.m3u.data.television.model.RemoteDirection
import kotlinx.coroutines.flow.SharedFlow

@Immutable
interface RemoteDirectionService {
    val incoming: SharedFlow<RemoteDirection>
    fun emit(remoteDirection: RemoteDirection)
}