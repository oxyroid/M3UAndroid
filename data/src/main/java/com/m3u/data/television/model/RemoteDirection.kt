package com.m3u.data.television.model

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