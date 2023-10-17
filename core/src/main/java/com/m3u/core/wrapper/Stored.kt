package com.m3u.core.wrapper

interface Stored<T, R> {
    fun R.restore(): T
    fun store(): R
}
