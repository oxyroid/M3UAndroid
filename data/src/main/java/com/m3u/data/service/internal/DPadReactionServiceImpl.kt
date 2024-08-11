package com.m3u.data.service.internal

import androidx.compose.runtime.Immutable
import com.m3u.core.architecture.dispatcher.Dispatcher
import com.m3u.core.architecture.dispatcher.M3uDispatchers.Main
import com.m3u.data.tv.model.RemoteDirection
import com.m3u.data.service.DPadReactionService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject

@Immutable
class DPadReactionServiceImpl @Inject constructor(
    @Dispatcher(Main) private val mainDispatcher: CoroutineDispatcher
) : DPadReactionService {
    override val incoming = MutableSharedFlow<RemoteDirection>()

    override suspend fun emit(remoteDirection: RemoteDirection) {
        withContext(mainDispatcher) {
            incoming.emit(remoteDirection)
        }
    }
}