package com.m3u.data.local.http.endpoint

import androidx.annotation.Keep
import com.m3u.core.architecture.Publisher
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.websocket.webSocket
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
data class SayHello @Inject constructor(
    private val publisher: Publisher
) : Endpoint {
    override fun apply(route: Route) {
        route.route("/say_hello") {
            get {
                call.respond(publisher.model)
            }
            webSocket {
                for (frame in incoming) {
                    val rep = Rep(
                        model = publisher.model,
                        version = publisher.versionCode,
                        snapshot = publisher.snapshot
                    )
                    call.respond(rep)
                }
            }
        }
    }

    @Keep
    @Serializable
    data class Rep(
        val model: String,
        val version: Int,
        val snapshot: Boolean
    )
}
