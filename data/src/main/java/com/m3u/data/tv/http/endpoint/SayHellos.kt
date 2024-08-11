package com.m3u.data.tv.http.endpoint

import com.m3u.core.architecture.Publisher
import com.m3u.core.architecture.logger.Logger
import com.m3u.core.architecture.logger.sandBox
import com.m3u.core.wrapper.Message
import com.m3u.data.repository.media.MediaRepository
import com.m3u.data.tv.model.TvInfo
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.websocket.sendSerialized
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readReason
import io.ktor.websocket.readText
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
data class SayHellos @Inject constructor(
    private val publisher: Publisher,
    @Logger.MessageImpl private val messager: Logger,
    private val mediaRepository: MediaRepository
) : Endpoint {
    private val info = with(publisher) {
        TvInfo(
            model = model,
            version = versionCode,
            snapshot = snapshot,
            abi = abi,
            allowUpdatedPackage = true
        )
    }

    override fun apply(route: Route) {
        route.route("/say_hello") {
            get {
                call.respond(info)
            }

            post("/install") {
                messager.sandBox {
                    val version = call.queryParameters["version"]
                    if (version == null) {
                        call.respond(
                            DefRep(
                                result = false,
                                reason = "require version query parameter"
                            )
                        )
                        return@post
                    }
                    mediaRepository.installApk(call.receiveChannel())
                    call.respond(
                        DefRep(true)
                    )
                }
            }

            webSocket {
                val model = call.request.queryParameters["model"] ?: "?"

                messager.log("Connection from [$model]", Message.LEVEL_INFO)
                sendSerialized(info)

                for (frame in incoming) {
                    when (frame) {
                        is Frame.Text -> {
                            messager.log("[$model] " + frame.readText(), Message.LEVEL_WARN)
                            sendSerialized(info)
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

}
