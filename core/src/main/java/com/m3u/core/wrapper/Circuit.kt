package com.m3u.core.wrapper

/**
 * flow-style data-message object wrapper.
 * @see Resource.circuit
 * @see Process.circuit
 * @see Process.percentCircuit
 */
class Circuit<T>(
    val data: () -> T? = { null },
    val message: () -> String? = { null }
) {
    private var currentData: T? = null
    fun getData(): T? = currentData ?: data().also { currentData = it }

    private var currentMessage: String? = null
    fun getMessage(): String? = currentMessage ?: message().also { currentMessage = it }
}
