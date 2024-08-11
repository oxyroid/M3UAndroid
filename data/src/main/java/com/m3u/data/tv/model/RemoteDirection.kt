package com.m3u.data.tv.model

import android.view.KeyEvent
import androidx.compose.runtime.Immutable

@Immutable
enum class RemoteDirection(val value: Int) {
    LEFT(0), RIGHT(1), UP(2), DOWN(3), ENTER(4), EXIT(5);

    companion object {
        fun of(value: Int): RemoteDirection? {
            return when (value) {
                0 -> LEFT
                1 -> RIGHT
                2 -> UP
                3 -> DOWN
                4 -> ENTER
                5 -> EXIT
                else -> null
            }
        }
    }
}

val RemoteDirection.keyCode
    get() = when (this) {
        RemoteDirection.LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
        RemoteDirection.RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT
        RemoteDirection.UP -> KeyEvent.KEYCODE_DPAD_UP
        RemoteDirection.DOWN -> KeyEvent.KEYCODE_DPAD_DOWN
        RemoteDirection.ENTER -> KeyEvent.KEYCODE_DPAD_CENTER
        RemoteDirection.EXIT -> KeyEvent.KEYCODE_BACK
    }