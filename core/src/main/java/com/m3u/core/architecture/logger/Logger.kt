package com.m3u.core.architecture.logger

import com.m3u.core.wrapper.Message
import javax.inject.Qualifier
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

interface Logger {
    fun log(
        text: String,
        level: Int = Message.LEVEL_ERROR,
        tag: String = "LOGGER",
        duration: Duration = 5.seconds,
        type: Int = Message.TYPE_SNACK
    )

    fun log(
        throwable: Throwable,
        tag: String = "LOGGER"
    )

    @Qualifier
    @Retention(AnnotationRetention.BINARY)
    annotation class MessageImpl
}

fun Logger.post(block: () -> Any?) {
    log(block().toString())
}

fun Logger.prefix(text: String): PrefixLogger = PrefixLogger(this, text)

class PrefixLogger(
    val delegate: Logger,
    private val prefix: String
) : Logger {
    override fun log(text: String, level: Int, tag: String, duration: Duration, type: Int) {
        delegate.log("${prefix}: $text", level, tag, duration, type)
    }

    override fun log(throwable: Throwable, tag: String) {
        delegate.log(RuntimeException(prefix, throwable), tag)
    }
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
