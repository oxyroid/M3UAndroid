package com.m3u.data.local.http

import androidx.annotation.Keep
import com.m3u.core.architecture.Publisher
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import javax.inject.Inject

sealed interface Endpoint {
    fun apply(route: Route)
    data class SayHello @Inject constructor(
        private val publisher: Publisher
    ) : Endpoint {
        override fun apply(route: Route) {
            route.route("/say_hello") {
                get {
                    val rep = SayHelloRep(
                        model = publisher.model,
                        version = publisher.versionCode,
                        snapshot = publisher.snapshot
                    )
                    call.respond(rep)
                }
            }
        }

        @Keep
        @Serializable
        data class SayHelloRep(
            val model: String,
            val version: Int,
            val snapshot: Boolean
        )
    }
}
