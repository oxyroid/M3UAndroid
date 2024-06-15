package com.m3u.data.leanback.http

interface HttpServer {
    fun start(port: Int)
    fun stop()
}
