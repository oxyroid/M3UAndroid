package com.m3u.data.television.http

interface HttpServer {
    fun start(port: Int)
    fun stop()
}
