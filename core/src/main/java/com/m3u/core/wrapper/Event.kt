package com.m3u.core.wrapper

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable

@Immutable
sealed class Event<out T> private constructor(
    private val data: T? = null
) {
    open var isHandled: Boolean = false

    /**
     * Get the data and consume it if is has not been consumed
     * @param block The data receiver, it will be invoked if the data has not been consumed
     */
    inline fun handle(block: (T) -> Unit) {
        if (!isHandled) {
            isHandled = true
            block.invoke(peek())
        }
    }

    /**
     * Get the data whether it is consumed or not, this behavior will not consume it as well
     */
    fun peek() = data!!

    /**
     * Event which cannot be consumed
     */
    @Immutable
    class Handled<out T> : Event<T>() {
        override var isHandled: Boolean = true
    }

    /**
     * Regular Event
     * @see eventOf
     * @hide
     */
    @Stable
    class Regular<out T>(data: T) : Event<T>(data)
}

/**
 * Make an event with data
 *
 * Top-level method for constructing EventImpl
 * @see Event.Regular
 */
fun <T> eventOf(data: T): Event.Regular<T> = Event.Regular(data)

fun <T> handledEvent(): Event.Handled<T> = Event.Handled()
