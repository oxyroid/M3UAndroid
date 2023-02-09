package com.m3u.data.source.parser

import androidx.annotation.CallSuper
import com.m3u.data.interceptor.Interceptable
import com.m3u.data.interceptor.Interceptor
import com.m3u.data.source.parser.m3u.M3UParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.URL
import java.util.stream.Stream

abstract class Parser<T, E> : Interceptable<E> {
    protected val mInterpolator = mutableListOf<Interceptor<E>>()
    abstract suspend fun parse(lines: Stream<String>)
    abstract fun get(): T
    open fun reset() {
        mInterpolator.clear()
    }

    @CallSuper
    override fun addInterceptor(interceptor: Interceptor<E>) {
        mInterpolator.add(interceptor)
    }

    companion object {
        fun newM3UParser() = M3UParser()
    }
}

suspend fun Parser<*, *>.parse(stream: InputStream) {
    withContext(Dispatchers.IO) {
        stream.bufferedReader().lines().use {
            this@parse.parse(it)
        }
    }
}

suspend fun Parser<*, *>.parse(url: URL) {
    withContext(Dispatchers.IO) {
        url.openConnection().getInputStream().use {
            this@parse.parse(it)
        }
    }
}

suspend fun Parser<*, *>.parse(url: String) {
    withContext(Dispatchers.IO) {
        parse(URL(url))
    }
}