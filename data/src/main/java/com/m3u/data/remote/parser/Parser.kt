package com.m3u.data.remote.parser

import java.io.InputStream
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

interface Parser<I, R> {
    suspend fun execute(input: I): R
}

fun <I, R> Parser<I, R>.executeAsFlow(input: I): Flow<R> = flow {
    val result = execute(input)
    emit(result)
}


suspend fun <R> Parser<InputStream, R>.execute(
    url: URL,
    connectTimeout: Int = 8000,
    readTimeout: Int = connectTimeout,
): R = withContext(Dispatchers.IO) {
    val connection = url.openConnection()
    connection.connectTimeout = connectTimeout
    connection.readTimeout = readTimeout
    connection.getInputStream().use {
        withContext(Dispatchers.Default) {
            this@execute.execute(it)
        }
    }
}

suspend fun <R> Parser<InputStream, R>.execute(
    url: String,
    connectTimeout: Int = 8000,
    readTimeout: Int = connectTimeout
) = execute(URL(url), connectTimeout, readTimeout)