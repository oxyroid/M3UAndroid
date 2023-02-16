package com.m3u.core.architecture

interface Logger {
    fun log(throwable: Throwable)
    fun readAll(): List<String>
}

abstract class AbstractLogger(val logger: Logger) {
    protected inline fun <R> sandbox(block: () -> R): R? = try {
        block()
    } catch (e: Exception) {
        logger.log(e)
        null
    }
}