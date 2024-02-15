package com.m3u.data.service.internal

import com.m3u.core.architecture.dispatcher.Dispatcher
import com.m3u.core.architecture.dispatcher.M3uDispatchers.Main
import com.m3u.data.service.RemoteDirectionService
import com.m3u.data.television.model.RemoteDirection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class RemoteDirectionServiceImpl @Inject constructor(
    @Dispatcher(Main) private val mainDispatcher: CoroutineDispatcher
) : RemoteDirectionService {
    private val _remoteDirection = MutableSharedFlow<RemoteDirection>()
    override val remoteDirection: SharedFlow<RemoteDirection> = _remoteDirection.asSharedFlow()

    private val coroutineScope = CoroutineScope(mainDispatcher)

    override fun handle(remoteDirection: RemoteDirection) {
        coroutineScope.launch {
            _remoteDirection.emit(remoteDirection)
        }
    }
}