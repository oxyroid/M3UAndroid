package com.m3u.core.wrapper

import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlin.experimental.ExperimentalTypeInference

sealed class Process<out T> {
    data class Loading(val value: ProcessValue) : Process<Nothing>()
    data class Success<out T>(val data: T) : Process<T>()
    data class Failure<out T>(val message: String?) : Process<T>()
}

@JvmInline
value class ProcessValue(val value: Int) {
    init {
        check(value in 0..100)
    }
}

context(FlowCollector<Process<T>>)
val <T> Int.process: ProcessValue get() = ProcessValue(this)

@OptIn(ExperimentalTypeInference::class)
fun <T> processFlow(@BuilderInference block: suspend FlowCollector<Process<T>>.() -> Unit) =
    flow<Process<T>> {
        emit(Process.Loading(0.process))
        block()
    }


suspend fun <T> FlowCollector<Process<T>>.emitResource(value: T) =
    emit(Process.Success(value))

suspend fun <T> FlowCollector<Process<T>>.emitMessage(message: String?) =
    emit(Process.Failure(message))

suspend fun <T> FlowCollector<Process<T>>.emitException(exception: Exception?) =
    emitMessage(exception?.message)
