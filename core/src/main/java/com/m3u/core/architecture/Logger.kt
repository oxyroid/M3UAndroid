package com.m3u.core.architecture

import android.util.Log
import java.io.File
import kotlinx.coroutines.flow.Flow

interface Logger {
    fun log(throwable: Throwable)
    fun readAll(): List<File>
    fun observe(): Flow<String>
}

abstract class AbstractLogger(val logger: Logger) {
    protected inline fun <R> sandbox(block: () -> R): R? = try {
        block()
    } catch (e: Exception) {
        logger.log(e)
        Log.e("AbstractLogger", "sandbox: ", e)
        null
    }

    protected inline fun <R> result(block: () -> R): Result<R> = try {
        val data = block()
        Result.success(data)
    } catch (e: Exception) {
        logger.log(e)
        Log.e("AbstractLogger", "resource", e)
        Result.failure(e)
    }
}