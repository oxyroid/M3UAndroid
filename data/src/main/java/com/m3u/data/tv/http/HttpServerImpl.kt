package com.m3u.data.tv.http

import com.m3u.data.tv.http.endpoint.Remotes
import com.m3u.data.tv.http.endpoint.Playlists
import com.m3u.data.tv.http.endpoint.SayHellos
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import kotlinx.serialization.json.Json
import java.time.Duration
import javax.inject.Inject

internal class HttpServerImpl @Inject constructor(
    private val sayHellos: SayHellos,
    private val playlists: Playlists,
    private val remotes: Remotes
) : HttpServer {
    private var server: EmbeddedServer<*, *>? = null

    override fun start(port: Int) {
        server = embeddedServer(Netty, port) {
            configureSerialization()
            configureSockets()
            configureCors()
            routing {
                sayHellos.apply(this)
                playlists.apply(this)
                remotes.apply(this)
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
            val json = Json {
                ignoreUnknownKeys = true
                prettyPrint = true
            }
            contentConverter = KotlinxWebsocketSerializationConverter(json)
            pingPeriod = Duration.ofSeconds(15)
            timeout = Duration.ofSeconds(15)
        }
    }

    private fun Application.configureCors() {
        install(CORS) {
            anyHost()
            allowSameOrigin = true
        }
    }
}
