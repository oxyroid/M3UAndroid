package com.m3u.core.architecture.logger

interface Logger {
    fun log(text: String)
    fun log(throwable: Throwable)
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

/**
 * Execute a returned lambda as a Result with logger.
 */
inline fun <R> Logger.executeResult(block: () -> R): Result<R> = try {
    val data = block()
    Result.success(data)
} catch (e: Exception) {
    log(e)
    Result.failure(e)
}
