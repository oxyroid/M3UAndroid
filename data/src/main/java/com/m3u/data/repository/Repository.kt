package com.m3u.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

interface Repository<T, ID> {
    fun observe(id: ID): Flow<T?>
    fun observeAll(): Flow<List<T>>
    suspend fun get(id: ID): T?
}

inline fun <T, ID> Repository<T, ID>.observeAll(
    crossinline predicate: (T) -> Boolean
): Flow<List<T>> = observeAll()
    .map { it.filter(predicate) }
    .distinctUntilChanged()