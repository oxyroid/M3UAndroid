package com.m3u.core.wrapper

class Circuit<T>(
    val data: () -> T? = { null },
    val message: () -> String? = { null }
) {
    private var currentData: T? = null
    fun getData(): T? = currentData ?: data().also { currentData = it }

    private var currentMessage: String? = null
    fun getMessage(): String? = currentMessage ?: message().also { currentMessage = it }
}
