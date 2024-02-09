package com.m3u.data.local.http.internal

import com.m3u.data.local.http.Endpoint
import com.m3u.data.local.http.HttpServer
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.channelFlow
import kotlinx.serialization.json.Json
import javax.inject.Inject

internal class HttpServerImpl @Inject constructor(
    private val sayHello: Endpoint.SayHello,
    private val playlists: Endpoint.Playlists
) : HttpServer {
    override fun start(port: Int) = channelFlow<Unit> {
        val server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration> =
            embeddedServer(Netty, port) {
                configureSerialization()
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
}
