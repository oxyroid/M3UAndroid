package com.m3u.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

interface ReadOnlyRepository<out T, in ID> {
    fun observe(id: ID): Flow<T?>
    fun observeAll(): Flow<List<T>>
    suspend fun get(id: ID): T?
}

inline fun <T> ReadOnlyRepository<T, *>.observeAll(
    crossinline predicate: (T) -> Boolean
): Flow<List<T>> = observeAll()
    .map { it.filter(predicate) }
    .distinctUntilChanged()

interface ReadWriteRepository<T, in ID> : ReadOnlyRepository<T, ID> {
    suspend fun save(e: T)
    suspend fun delete(e: T)
    suspend fun deleteAll()
}
