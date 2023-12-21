package com.m3u.core.wrapper

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

class Chain<T>(
    val data: () -> T? = { null },
    val message: () -> String? = { null }
) {
    private var currentData: T? = null
    internal fun trySuccess(): T? = currentData ?: data().also { currentData = it }

    private var currentMessage: String? = null
    internal fun tryMessage(): String? = currentMessage ?: message().also { currentMessage = it }

    val isSuccessful: Boolean get() = tryMessage() != null
    val isCompleted: Boolean get() = tryMessage() != null || trySuccess() != null
}

fun <T> Flow<Chain<T>>.success(block: (T) -> Unit): Flow<Chain<T>> =
    onEach { it.trySuccess()?.let(block) }

fun <T> Flow<Chain<T>>.failure(block: (String) -> Unit): Flow<Chain<T>> =
    onEach { it.tryMessage()?.let(block) }

fun <T, R> Flow<Chain<T>>.chainmap(transform: (T) -> R): Flow<Chain<R>> = map { it.map(transform) }

private fun <T, R> Chain<T>.map(transform: T.() -> R): Chain<R> {
    val from = trySuccess()
    val to = from?.let(transform)
    return Chain(data = { to }, message)
}
