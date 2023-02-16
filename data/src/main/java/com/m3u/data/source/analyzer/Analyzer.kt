package com.m3u.data.source.analyzer

import com.m3u.data.source.analyzer.m3u.M3UAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.URL
import java.util.stream.Stream

abstract class Analyzer<T> {
    abstract suspend fun analyze(lines: Stream<String>)
    abstract fun get(): T
    abstract fun reset()

    companion object {
        fun newM3UParser() = M3UAnalyzer()
    }
}

suspend fun Analyzer<*>.analyze(stream: InputStream) {
    withContext(Dispatchers.Default) {
        stream.bufferedReader().lines().use {
            this@analyze.analyze(it)
        }
    }
}

suspend fun Analyzer<*>.analyze(
    url: URL,
    connectTimeout: Int = 8000,
    readTimeout: Int = connectTimeout,
) {
    withContext(Dispatchers.IO) {
        val connection = url.openConnection()
        connection.connectTimeout = connectTimeout
        connection.readTimeout = readTimeout
        connection.getInputStream().use {
            this@analyze.analyze(it)
        }
    }
}

suspend fun Analyzer<*>.analyze(
    url: String,
    connectTimeout: Int = 8000,
    readTimeout: Int = connectTimeout
) = analyze(URL(url), connectTimeout, readTimeout)