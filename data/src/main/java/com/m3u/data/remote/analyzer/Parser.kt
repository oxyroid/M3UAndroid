package com.m3u.data.remote.analyzer

import com.m3u.data.remote.analyzer.impl.M3UParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.URL
import java.util.stream.Stream
import kotlin.streams.asSequence

abstract class Parser<T> {
    abstract suspend fun execute(lines: Sequence<String>)
    abstract fun get(): T

    companion object {
        fun newM3UParser() = M3UParser()
    }
}

suspend fun Parser<*>.execute(stream: Stream<String>) {
    this@execute.execute(stream.asSequence())
}

suspend fun Parser<*>.execute(stream: InputStream) {
    stream.bufferedReader().lines().use {
        this@execute.execute(it)
    }
}

suspend fun Parser<*>.execute(
    url: URL,
    connectTimeout: Int = 8000,
    readTimeout: Int = connectTimeout,
) {
    withContext(Dispatchers.IO) {
        val connection = url.openConnection()
        connection.connectTimeout = connectTimeout
        connection.readTimeout = readTimeout
        connection.getInputStream().use {
            this@execute.execute(it)
        }
    }
}

suspend fun Parser<*>.execute(
    url: String,
    connectTimeout: Int = 8000,
    readTimeout: Int = connectTimeout
) = execute(URL(url), connectTimeout, readTimeout)