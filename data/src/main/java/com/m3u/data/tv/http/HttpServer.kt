package com.m3u.data.tv.http

interface HttpServer {
    fun start(port: Int)
    fun stop()
}
