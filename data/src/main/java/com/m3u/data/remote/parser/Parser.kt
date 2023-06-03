package com.m3u.data.remote.parser

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.Proxy
import java.net.URL

interface Parser<I, R> {
    suspend fun execute(input: I): R
}

suspend fun <R> Parser<InputStream, R>.execute(
    url: URL,
    connectTimeout: Int = 8000,
    readTimeout: Int = connectTimeout,
    proxy: Proxy = Proxy.NO_PROXY
): R = withContext(Dispatchers.IO) {
    val connection = url.openConnection(proxy)
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
    readTimeout: Int = connectTimeout,
    proxy: Proxy = Proxy.NO_PROXY
) = execute(URL(url), connectTimeout, readTimeout, proxy)

suspend fun <R> Parser<InputStream, R>.execute(
    content: String
) = execute(content.byteInputStream())