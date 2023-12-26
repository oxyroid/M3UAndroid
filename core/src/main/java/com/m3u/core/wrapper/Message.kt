package com.m3u.core.wrapper

import androidx.compose.runtime.Immutable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

sealed class Message(
    open val level: Int,
    open val tag: String,
    open val type: Int,
    open val duration: Duration = 3.seconds
) {
    @Immutable
    abstract class Static(
        override val level: Int,
        override val tag: String,
        override val type: Int,
        override val duration: Duration = 3.seconds,
        val resId: Int,
        vararg val formatArgs: Any
    ) : Message(level, tag, type, duration) {
        companion object : Static(
            level = LEVEL_EMPTY,
            tag = "",
            type = TYPE_EMPTY,
            resId = 0,
            formatArgs = emptyArray(),
            duration = 0.seconds
        )
    }

    @Immutable
    data class Dynamic(
        val value: String,
        override val level: Int,
        override val tag: String,
        override val type: Int,
        override val duration: Duration = 3.seconds
    ) : Message(level, tag, type, duration) {
        companion object {
            val EMPTY = Dynamic("", LEVEL_EMPTY, "", TYPE_EMPTY)
        }
    }

    companion object {
        const val LEVEL_EMPTY = 0
        const val LEVEL_INFO = 1
        const val LEVEL_WARN = 2
        const val LEVEL_ERROR = 3

        const val TYPE_EMPTY = 0
        const val TYPE_TOAST = 1
        const val TYPE_SNACK = 2
        const val TYPE_NOTIFICATION = 3
        const val TYPE_DIALOG = 4
    }
}
