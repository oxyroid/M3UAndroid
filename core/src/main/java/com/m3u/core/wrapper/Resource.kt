@file:Suppress("unused")

package com.m3u.core.wrapper

import kotlin.experimental.ExperimentalTypeInference
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow

sealed class Resource<out T> {
    object Loading : Resource<Nothing>()
    data class Success<out T>(
        val data: T
    ) : Resource<T>()

    data class Failure<out T>(
        val message: String?
    ) : Resource<T>()
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

suspend fun <T> FlowCollector<Resource<T>>.emitResource(value: T) = emit(Resource.Success(value))

suspend fun <T> FlowCollector<Resource<T>>.emitMessage(message: String?) =
    emit(Resource.Failure(message))

suspend fun <T> ProducerScope<Resource<T>>.sendResource(value: T) = send(Resource.Success(value))

suspend fun <T> ProducerScope<Resource<T>>.sendMessage(message: String?) =
    send(Resource.Failure(message))