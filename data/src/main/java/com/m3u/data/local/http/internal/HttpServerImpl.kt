package com.m3u.data.local.http.internal

import com.m3u.data.local.http.HttpServer
import com.m3u.data.local.http.endpoint.Playlists
import com.m3u.data.local.http.endpoint.SayHello
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.channelFlow
import kotlinx.serialization.json.Json
import java.time.Duration
import javax.inject.Inject

internal class HttpServerImpl @Inject constructor(
    private val sayHello: SayHello,
    private val playlists: Playlists
) : HttpServer {
    override fun start(port: Int) = channelFlow<Unit> {
        val server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration> =
            embeddedServer(Netty, port) {
                configureSerialization()
                configureSockets()
                routing {
                    sayHello.apply(this)
                    playlists.apply(this)
                }
            }
                .start(wait = false)
        awaitClose {
            server.stop()
        }
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
}
