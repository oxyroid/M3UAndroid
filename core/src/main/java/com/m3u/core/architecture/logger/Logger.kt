package com.m3u.core.architecture.logger

interface Logger {
    fun log(text: String)
    fun log(throwable: Throwable)
}

inline fun Logger.sandBox(block: () -> Unit) {
    try {
        block()
    } catch (e: Exception) {
        log(e)
    }
}

inline fun <R> Logger.execute(block: () -> R): R? = try {
    block()
} catch (e: Exception) {
    log(e)
    null
}

inline fun <R> Logger.executeResult(block: () -> R): Result<R> = try {
    val data = block()
    Result.success(data)
} catch (e: Exception) {
    log(e)
    Result.failure(e)
}
