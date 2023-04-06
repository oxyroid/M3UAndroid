package com.m3u.data.remote.parser

import java.io.InputStream
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface Parser<I, R> {
    suspend fun execute(input: I): R
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