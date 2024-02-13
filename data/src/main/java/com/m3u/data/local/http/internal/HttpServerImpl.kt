package com.m3u.data.local.http.internal

import com.m3u.data.local.http.HttpServer
import com.m3u.data.local.http.endpoint.Playlists
import com.m3u.data.local.http.endpoint.SayHello
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respondText
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import kotlinx.serialization.json.Json
import java.time.Duration
import javax.inject.Inject

internal class HttpServerImpl @Inject constructor(
    private val sayHello: SayHello,
    private val playlists: Playlists
) : HttpServer {
    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null

    override fun start(port: Int) {
        server = embeddedServer(Netty, port) {
            configureSerialization()
            configureSockets()
            configureCors()
            configureStatusPages()
            routing {
                sayHello.apply(this)
                playlists.apply(this)
            }
        }.apply {
            start(false)
        }
    }

    override fun stop() {
        server?.stop()
        server = null
    }

    private fun Application.configureSerialization() {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    prettyPrint = true
                }
            )
        }
    }

    private fun Application.configureSockets() {
        install(WebSockets) {
            pingPeriod = Duration.ofSeconds(15)
            timeout = Duration.ofSeconds(15)
        }
    }

    private fun Application.configureCors() {
        install(CORS) {
            allowSameOrigin = true
            allowCredentials = true
            anyHost()
            allowXHttpMethodOverride()
        }
    }

    private fun Application.configureStatusPages() {
        install(StatusPages) {
            exception { call: ApplicationCall, cause: Exception ->
                call.respondText("${cause.message}")
            }
        }
    }
}
