package com.m3u.data.service.internal

import android.view.KeyEvent
import androidx.compose.runtime.Immutable
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

@Immutable
class RemoteDirectionServiceImpl @Inject constructor(
    @Dispatcher(Main) private val mainDispatcher: CoroutineDispatcher
) : RemoteDirectionService {
    private val coroutineScope = CoroutineScope(mainDispatcher)
    private val _actions = MutableSharedFlow<RemoteDirectionService.Action>()
    override val actions: SharedFlow<RemoteDirectionService.Action> = _actions.asSharedFlow()

    override fun emit(remoteDirection: RemoteDirection) {
        val keyCode = when (remoteDirection) {
            RemoteDirection.LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
            RemoteDirection.RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT
            RemoteDirection.UP -> KeyEvent.KEYCODE_DPAD_UP
            RemoteDirection.DOWN -> KeyEvent.KEYCODE_DPAD_DOWN
            RemoteDirection.ENTER -> KeyEvent.KEYCODE_DPAD_CENTER
            RemoteDirection.EXIT -> {
                coroutineScope.launch {
                    _actions.emit(
                        RemoteDirectionService.Action.Back
                    )
                }
                return
            }
        }
        coroutineScope.launch {
            _actions.emit(
                RemoteDirectionService.Action.Common(keyCode)
            )
        }
    }
}