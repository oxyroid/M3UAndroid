package com.m3u.core.architecture

import android.util.Log
import java.io.File

interface Logger {
    fun log(throwable: Throwable)
    fun readAll(): List<File>
}

abstract class AbstractLogger(val logger: Logger) {
    protected inline fun <R> sandbox(block: () -> R): R? = try {
        block()
    } catch (e: Exception) {
        logger.log(e)
        Log.e("TAG", "sandbox: ", e)
        null
    }
}