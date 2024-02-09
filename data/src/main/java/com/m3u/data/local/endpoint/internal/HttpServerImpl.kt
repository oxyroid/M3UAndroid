package com.m3u.data.local.endpoint.internal

import com.m3u.data.local.endpoint.Endpoint
import com.m3u.data.local.endpoint.HttpServer
import io.javalin.Javalin
import io.javalin.router.JavalinDefaultRoutingApi
import io.javalin.router.RoutingApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.channelFlow
import kotlinx.serialization.json.Json
import javax.inject.Inject

internal class HttpServerImpl @Inject constructor(
    private val sayHello: Endpoint.SayHello
) : HttpServer {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }
    private val jsonMapper by lazy {
        KotlinxSerializationJsonMapper(json)
    }

    override fun start(port: Int) = channelFlow<Unit> {
        val app = Javalin
            .create { config ->
                config.jsonMapper(jsonMapper)
            }
            .get("/") { ctx -> ctx.result("Hello World") }
            .endpoint(sayHello)
            .start(port)

        awaitClose {
            app.stop()
        }
    }

    private fun <API : RoutingApi> JavalinDefaultRoutingApi<API>.endpoint(endpoint: Endpoint): API =
        addHttpHandler(endpoint.type, endpoint.path) { endpoint(it) }
}