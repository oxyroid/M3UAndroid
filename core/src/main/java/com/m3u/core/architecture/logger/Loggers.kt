package com.m3u.core.architecture.logger

import kotlin.time.Duration

inline fun Logger.sandBox(block: () -> Unit) {
    runCatching { block() }.onFailure { log(it) }
}

inline fun <R> Logger.execute(block: () -> R): R? = runCatching { block() }
    .onFailure { log(it) }
    .getOrNull()

fun Logger.post(block: () -> Any?) {
    log(block().toString())
}

fun Logger.install(profile: Profile): Logger = if (!profile.enabled) EmptyLogger
else PrefixLogger(this, profile.name)

private class PrefixLogger(
    private val delegate: Logger,
    private val prefix: String
) : Logger {
    override fun log(text: String, level: Int, tag: String, duration: Duration, type: Int) {
        delegate.log("${prefix}: $text", level, tag, duration, type)
    }

    override fun log(throwable: Throwable, tag: String) {
        delegate.log(RuntimeException(prefix, throwable), tag)
    }
}

private object EmptyLogger : Logger {
    override fun log(text: String, level: Int, tag: String, duration: Duration, type: Int) {}
    override fun log(throwable: Throwable, tag: String) {}
}
