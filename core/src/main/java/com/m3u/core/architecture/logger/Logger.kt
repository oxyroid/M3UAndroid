package com.m3u.core.architecture.logger

import com.m3u.core.wrapper.Message.Companion.LEVEL_ERROR
import javax.inject.Qualifier
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

interface Logger {
    fun log(
        text: String,
        level: Int = LEVEL_ERROR,
        tag: String = "LOGGER",
        duration: Duration = 3.seconds
    )

    fun log(
        throwable: Throwable,
        tag: String = "LOGGER"
    )

    @Qualifier
    @Retention(AnnotationRetention.BINARY)
    annotation class Message
}

/**
 * Execute a none-returned lambda with logger.
 */
inline fun Logger.sandBox(block: () -> Unit) {
    try {
        block()
    } catch (e: Exception) {
        log(e)
    }
}

/**
 * Execute a returned lambda with logger.
 */
inline fun <R> Logger.execute(block: () -> R): R? = try {
    block()
} catch (e: Exception) {
    log(e)
    null
}
