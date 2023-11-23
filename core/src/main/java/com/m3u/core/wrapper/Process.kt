package com.m3u.core.wrapper

import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlin.experimental.ExperimentalTypeInference

sealed class Process<out T> {
    data class Loading(val value: Percent) : Process<Nothing>()
    data class Success<out T>(val data: T) : Process<T>()
    data class Failure<out T>(val message: String?) : Process<T>()
}

fun <T> Process<T>.circuit(): Circuit<T> {
    return when (this) {
        is Process.Success -> Circuit(data = { data })
        is Process.Failure -> Circuit(message = { message })
        is Process.Loading -> Circuit()
    }
}

fun Process<*>.percentCircuit(): Circuit<Percent> {
    return when (this) {
        is Process.Success -> Circuit(data = { 100.pt })
        is Process.Failure -> Circuit(message = { message })
        is Process.Loading -> Circuit(data = { value })
    }
}

@JvmInline
value class Percent(val value: Int) {
    init {
        check(value in 0..100)
    }
}

val Int.pt: Percent get() = Percent(this)

@OptIn(ExperimentalTypeInference::class)
fun <T> processFlow(@BuilderInference block: suspend FlowCollector<Process<T>>.() -> Unit) =
    flow {
        emit(Process.Loading(0.pt))
        block()
    }


suspend fun <T> FlowCollector<Process<T>>.emitResource(value: T) =
    emit(Process.Success(value))

suspend fun <T> FlowCollector<Process<T>>.emitMessage(message: String?) =
    emit(Process.Failure(message))

suspend fun <T> FlowCollector<Process<T>>.emitException(exception: Exception?) =
    emitMessage(exception?.message)
