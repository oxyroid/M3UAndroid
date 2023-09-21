@file:Suppress("unused")

package com.m3u.core.wrapper

import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlin.experimental.ExperimentalTypeInference

sealed class Resource<out T> {
    data object Loading : Resource<Nothing>()
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

sealed class ProgressResource<out T> {
    data class Loading(val value: Int) : ProgressResource<Nothing>()
    data class Success<out T>(
        val data: T
    ) : ProgressResource<T>()

    data class Failure<out T>(
        val message: String?
    ) : ProgressResource<T>()
}

@JvmName("emitProgressProgress")
suspend fun <T> FlowCollector<ProgressResource<T>>.emitProgress(value: Int) =
    emit(ProgressResource.Loading(value))

@JvmName("emitProgressResource")
suspend fun <T> FlowCollector<ProgressResource<T>>.emitResource(value: T) =
    emit(ProgressResource.Success(value))

@JvmName("emitProgressMessage")
suspend fun <T> FlowCollector<ProgressResource<T>>.emitMessage(message: String?) =
    emit(ProgressResource.Failure(message))

@JvmName("emitProgressException")
suspend fun <T> FlowCollector<ProgressResource<T>>.emitException(exception: Exception?) =
    emitMessage(exception?.message)
