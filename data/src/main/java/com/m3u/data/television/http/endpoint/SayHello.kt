package com.m3u.data.television.http.endpoint

import androidx.annotation.Keep
import androidx.compose.runtime.Immutable
import com.m3u.core.architecture.Abi
import com.m3u.core.architecture.Publisher
import com.m3u.core.architecture.logger.Logger
import com.m3u.core.wrapper.Message
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.websocket.sendSerialized
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readReason
import io.ktor.websocket.readText
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
data class SayHello @Inject constructor(
    private val publisher: Publisher,
    @Logger.MessageImpl private val messager: Logger
) : Endpoint {
    override fun apply(route: Route) {
        route.route("/say_hello") {
            get {
                val rep = Rep(
                    model = publisher.model,
                    version = publisher.versionCode,
                    snapshot = publisher.snapshot,
                    abi = publisher.abi
                )
                call.respond(rep)
            }
            webSocket {
                val model = call.request.queryParameters["model"] ?: "?"
                val rep = with(publisher) { Rep(model, versionCode, snapshot, abi) }

                messager.log("Connection from [$model]", Message.LEVEL_INFO)
                sendSerialized(rep)

                for (frame in incoming) {
                    when (frame) {
                        is Frame.Text -> {
                            messager.log("[$model] " + frame.readText(), Message.LEVEL_WARN)
                            sendSerialized(rep)
                        }

                        is Frame.Binary -> {

                        }
                        is Frame.Close -> {
                            messager.log("Connection lost from [$model], reason: ${frame.readReason()}")
                        }

                        else -> {}
                    }
                }
            }
        }
    }

    @Keep
    @Serializable
    @Immutable
    data class Rep(
        val model: String,
        val version: Int,
        val snapshot: Boolean,
        val abi: Abi
    )
}
