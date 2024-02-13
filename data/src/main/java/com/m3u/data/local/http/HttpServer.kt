package com.m3u.data.local.http

interface HttpServer {
    fun start(port: Int)
    fun stop()
}
