package com.m3u.data.service.internal

import androidx.compose.runtime.Immutable
import com.m3u.data.service.DPadReactionService
import com.m3u.data.tv.model.RemoteDirection
import kotlinx.coroutines.flow.MutableSharedFlow
import javax.inject.Inject

@Immutable
class DPadReactionServiceImpl @Inject constructor() : DPadReactionService {
    override val incoming = MutableSharedFlow<RemoteDirection>()

    override suspend fun emit(remoteDirection: RemoteDirection) {
        incoming.emit(remoteDirection)
    }
}