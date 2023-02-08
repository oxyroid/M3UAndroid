package com.m3u.core.architecture

interface Logger {
    fun log(throwable: Throwable)
    fun readAll(): List<String>
}