package com.m3u.core.architecture.logger

import com.m3u.core.wrapper.Message
import javax.inject.Qualifier
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

interface Logger {
    fun log(
        text: String,
        level: Int = Message.LEVEL_INFO,
        tag: String = Thread.currentThread().name,
        duration: Duration = 5.seconds,
        type: Int = Message.TYPE_SNACK
    )

    fun log(
        throwable: Throwable,
        level: Int = Message.LEVEL_ERROR,
        tag: String = Thread.currentThread().name
    )

    @Qualifier
    @Retention(AnnotationRetention.BINARY)
    annotation class MessageImpl
}

inline fun Logger.sandBox(block: () -> Unit) {
    runCatching { block() }.onFailure { log(it) }
}

inline fun <R> Logger.execute(block: () -> R): R? = runCatching { block() }
    .onFailure {
        log(
            throwable = it,
            level = Message.LEVEL_ERROR,
        )
    }
    .getOrNull()

fun Logger.post(level: Int = Message.LEVEL_INFO, block: () -> Any?) {
    val result = runCatching { block() }
    if (result.isSuccess) {
        log(
            text = result.getOrNull().toString(),
            level = level
        )
    } else {
        log(
            throwable = result.exceptionOrNull()!!,
            level = Message.LEVEL_ERROR
        )
    }
}

fun Logger.install(profile: Profile): Logger = PrefixLogger(
    LevelLogger(this, profile.level), profile.name
)

private class LevelLogger(
    private val delegate: Logger,
    private val limitLevel: Int
) : Logger {
    override fun log(text: String, level: Int, tag: String, duration: Duration, type: Int) {
        if (limitLevel <= level) {
            delegate.log(text, level, tag, duration, type)
        }
    }

    override fun log(throwable: Throwable, level: Int, tag: String) {
        if (limitLevel >= level) {
            delegate.log(throwable, level, tag)
        }
    }
}

private class PrefixLogger(
    private val delegate: Logger,
    private val prefix: String
) : Logger {
    override fun log(text: String, level: Int, tag: String, duration: Duration, type: Int) {
        delegate.log("${prefix}: $text", level, tag, duration, type)
    }

    override fun log(throwable: Throwable, level: Int, tag: String) {
        delegate.log(RuntimeException(prefix, throwable), level, tag)
    }
}
