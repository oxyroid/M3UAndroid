@file:Suppress("unused")

package com.m3u.core.wrapper

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlin.experimental.ExperimentalTypeInference

sealed class Resource<out T> {
    @Immutable
    data object Loading : Resource<Nothing>()

    @Stable
    data class Success<out T>(
        val data: T
    ) : Resource<T>()

    @Stable
    data class Failure<out T>(
        val message: String?
    ) : Resource<T>()
}

fun <T> Flow<Resource<T>>.chain(): Flow<Chain<T>> = map {
    when (it) {
        is Resource.Success -> Chain(data = { it.data })
        is Resource.Failure -> Chain(message = { it.message })
        else -> Chain()
    }
}

@OptIn(ExperimentalTypeInference::class)
fun <T> resourceFlow(@BuilderInference block: suspend FlowCollector<Resource<T>>.() -> Unit) =
    flow {
        emit(Resource.Loading)
        block()
    }

@OptIn(ExperimentalTypeInference::class)
fun <T> resourceChannelFlow(@BuilderInference block: suspend ProducerScope<Resource<T>>.() -> Unit) =
    channelFlow {
        send(Resource.Loading)
        block()
    }

@JvmName("emitResource")
suspend fun <T> FlowCollector<Resource<T>>.emitResource(value: T) = emit(Resource.Success(value))

@JvmName("emitMessage")
suspend fun <T> FlowCollector<Resource<T>>.emitMessage(message: String?) =
    emit(Resource.Failure(message))

@JvmName("emitException")
suspend fun <T> FlowCollector<Resource<T>>.emitException(exception: Exception?) =
    emitMessage(exception?.message)

suspend fun <T> ProducerScope<Resource<T>>.sendResource(value: T) = send(Resource.Success(value))

suspend fun <T> ProducerScope<Resource<T>>.sendMessage(message: String?) =
    send(Resource.Failure(message))

suspend fun <T> ProducerScope<Resource<T>>.sendException(exception: Exception?) =
    sendMessage(exception?.message)
