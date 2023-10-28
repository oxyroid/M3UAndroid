package com.m3u.dlna.http

import android.content.Context
import com.m3u.dlna.Utils

interface HttpServer {
    fun startServer()
    fun stopServer()
    fun isRunning(): Boolean
}

class LocalServer(
    context: Context,
    private val port: Int = 8192,
    jetty: Boolean = true,
    httpServer: HttpServer = if (jetty) JettyHttpServer(port) else NanoHttpServer(port),
) : HttpServer by httpServer {
    val ip: String = Utils.getWiFiIpAddress(context)
    val baseUrl: String = "http://$ip:$port"
}