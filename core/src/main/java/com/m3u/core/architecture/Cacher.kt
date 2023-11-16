package com.m3u.core.architecture

import java.io.File

interface Cacher<K, V, W> {
    fun readAll(): List<V>
    fun read(key: K): V?
    fun write(value: W)
}

@JvmInline
value class FilePath(val path: String)

interface FilePathCacher : Cacher<FilePath, File, Throwable>
