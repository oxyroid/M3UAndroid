package com.m3u.core.architecture

import java.io.File

interface TraceFileProvider {
    fun readAll(): List<File>
    fun read(path: String): File?
    fun write(value: Throwable)
}
