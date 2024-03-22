package com.m3u.core.wrapper

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

@Immutable
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

fun <T> Resource<T>.takeOrElse(block: () -> T): T = if (this is Resource.Success) this.data
else block()

fun <T, R> Flow<Resource<T>>.mapResource(transform: (T) -> R): Flow<Resource<R>> = map {
    when (it) {
        Resource.Loading -> Resource.Loading
        is Resource.Success -> Resource.Success(transform(it.data))
        is Resource.Failure -> Resource.Failure(it.message)
    }
}

fun <T> Flow<T>.flattenResource(): Flow<Resource<T>> = map<T, Resource<T>> { Resource.Success(it) }
    .onStart { emit(Resource.Loading) }
    .catch { emit(Resource.Failure(it.message)) }

inline fun <T> resourceflow(block: () -> T): Flow<Resource<T>> = flowOf(block()).flattenResource()
