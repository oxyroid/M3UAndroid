package com.m3u.core.wrapper

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

@Immutable
sealed class Resource<out T> {
    @Immutable
    data object Loading : Resource<Nothing>()

    @Immutable
    data class Success<out T>(val data: T) : Resource<T>()

    @Immutable
    data class Failure<out T>(val message: String?) : Resource<T>()
}

fun <T, R> Flow<Resource<T>>.mapResource(transform: (T) -> R): Flow<Resource<R>> = map {
    when (it) {
        Resource.Loading -> Resource.Loading
        is Resource.Success -> try {
            Resource.Success(transform(it.data))
        } catch (e: Exception) {
            Resource.Failure(e.message)
        }
        is Resource.Failure -> Resource.Failure(it.message)
    }
}

fun <T> Flow<T>.asResource(): Flow<Resource<T>> = map<T, Resource<T>> { Resource.Success(it) }
    .onStart { emit(Resource.Loading) }
    .catch { emit(Resource.Failure(it.message ?: it::class.simpleName)) }

fun <T> resource(block: suspend () -> T): Flow<Resource<T>> = channelFlow<Resource<T>> {
    try {
        trySend(Resource.Success(block()))
    } catch (e: Exception) {
        trySend(Resource.Failure(e.message ?: e::class.simpleName))
    }
}
    .onStart { emit(Resource.Loading) }
    .catch { emit(Resource.Failure(it.message ?: it::class.simpleName)) }
