package com.m3u.data.interceptor

interface Interceptor<T> {
    fun onPreHandle(line: String) {}
    fun onHandle(value: T) {}
}

interface Interceptable<T> {
    fun addInterceptor(interceptor: Interceptor<T>)
}