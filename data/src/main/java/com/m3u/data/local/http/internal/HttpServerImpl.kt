package com.m3u.data.local.http.internal

import com.m3u.core.architecture.Publisher
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import javax.inject.Inject

internal class HttpServerImpl @Inject constructor(
    private val sayHello: Endpoint.SayHello
) : HttpServer {
    override fun start(port: Int) = channelFlow<Unit> {
        val server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration> =
            embeddedServer(Netty, port) {
                configureSerialization()
                routing {
                    sayHello.apply(this)
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

fun main() = runBlocking {
    HttpServerImpl(
        Endpoint.SayHello(
            object : Publisher {
                override val applicationId: String = "applicationId"
                override val versionName: String = "versionName"
                override val versionCode: Int = 1
                override val debug: Boolean = false
                override val snapshot: Boolean = true
                override val model: String = "model"
                override val isTelevision: Boolean = true
            }
        )
    )
        .start(1223)
        .collect()
}
