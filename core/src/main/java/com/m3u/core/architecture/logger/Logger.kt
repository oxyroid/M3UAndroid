package com.m3u.core.architecture.logger

import java.io.File

interface Logger {
    fun log(s: String)
    fun log(throwable: Throwable)
    fun readAll(): List<File>
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
