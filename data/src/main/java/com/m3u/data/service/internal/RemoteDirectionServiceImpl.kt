package com.m3u.data.service.internal

import androidx.compose.runtime.Immutable
import com.m3u.core.architecture.dispatcher.Dispatcher
import com.m3u.core.architecture.dispatcher.M3uDispatchers.Main
import com.m3u.data.service.RemoteDirectionService
import com.m3u.data.television.model.RemoteDirection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
class RemoteDirectionServiceImpl @Inject constructor(
    @Dispatcher(Main) private val mainDispatcher: CoroutineDispatcher
) : RemoteDirectionService {
    private val coroutineScope = CoroutineScope(mainDispatcher)
    override val incoming = MutableSharedFlow<RemoteDirection>()

    override fun emit(remoteDirection: RemoteDirection) {
        coroutineScope.launch {
            incoming.emit(remoteDirection)
        }
    }
}