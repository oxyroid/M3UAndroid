package com.m3u.data.parser

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.URL
import java.util.stream.Stream

interface Parser<T> {
    suspend fun parse(lines: Stream<String>)
    fun get(): T
    fun reset()

    companion object {
        fun newM3UParser() = M3UParser()
    }
}

suspend fun Parser<*>.parse(stream: InputStream) {
    withContext(Dispatchers.IO) {
        stream.bufferedReader().lines().use {
            this@parse.parse(it)
        }
    }
}

suspend fun Parser<*>.parse(url: URL) {
    withContext(Dispatchers.IO) {
        url.openConnection().getInputStream().use {
            this@parse.parse(it)
        }
    }
}