package com.m3u.data.remote.parser

import com.m3u.data.remote.parser.impl.DefaultPlaylistParser
import java.io.InputStream
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

abstract class Parser<T> {
    abstract suspend fun execute(stream: InputStream): T

    companion object {
        fun newM3UParser() = DefaultPlaylistParser()
    }
}

suspend fun <T> Parser<T>.execute(
    url: URL,
    connectTimeout: Int = 8000,
    readTimeout: Int = connectTimeout,
): T = withContext(Dispatchers.IO) {
    val connection = url.openConnection()
    connection.connectTimeout = connectTimeout
    connection.readTimeout = readTimeout
    connection.getInputStream().use {
        withContext(Dispatchers.Default) {
            this@execute.execute(it)
        }
    }
}

suspend fun <T> Parser<T>.execute(
    url: String,
    connectTimeout: Int = 8000,
    readTimeout: Int = connectTimeout
) = execute(URL(url), connectTimeout, readTimeout)