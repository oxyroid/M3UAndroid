package com.m3u.data.service.internal

import android.view.KeyEvent
import android.view.inputmethod.BaseInputConnection
import androidx.compose.runtime.Immutable
import com.m3u.core.architecture.dispatcher.Dispatcher
import com.m3u.core.architecture.dispatcher.M3uDispatchers.Main
import com.m3u.data.service.RemoteDirectionService
import com.m3u.data.television.model.RemoteDirection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

@Immutable
class RemoteDirectionServiceImpl @Inject constructor(
    @Dispatcher(Main) private val mainDispatcher: CoroutineDispatcher
) : RemoteDirectionService {
    private val coroutineScope = CoroutineScope(mainDispatcher)

    override fun emit(remoteDirection: RemoteDirection) {
        val currentConnection = connection ?: return
        val keyCode = when (remoteDirection) {
            RemoteDirection.LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
            RemoteDirection.RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT
            RemoteDirection.UP -> KeyEvent.KEYCODE_DPAD_UP
            RemoteDirection.DOWN -> KeyEvent.KEYCODE_DPAD_DOWN
            RemoteDirection.ENTER -> KeyEvent.KEYCODE_DPAD_CENTER
            RemoteDirection.EXIT -> {
                coroutineScope.launch {
                    onBackPressed?.invoke()
                }
                return
            }
        }
        coroutineScope.launch {
            currentConnection.sendKeyEvent(
                KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
            )
            delay(150.milliseconds)
            currentConnection.sendKeyEvent(
                KeyEvent(KeyEvent.ACTION_UP, keyCode)
            )
        }
    }

    private var connection: BaseInputConnection? = null
    private var onBackPressed: (() -> Unit)? = null
    override fun init(
        connection: BaseInputConnection?,
        onBackPressed: (() -> Unit)?
    ) {
        this.connection = connection
        this.onBackPressed = onBackPressed
    }
}