package com.m3u.data.tv.http.endpoint

import com.m3u.core.foundation.architecture.Publisher
import com.m3u.data.tv.model.TvInfo
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.websocket.sendSerialized
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
data class SayHellos @Inject constructor(
    private val publisher: Publisher
) : Endpoint {
    private val info = with(publisher) {
        TvInfo(
            model = model,
            version = versionCode,
            abi = abi,
            allowUpdatedPackage = true
        )
    }

    override fun apply(route: Route) {
        route.route("/say_hello") {
            get { call.respond(info) }
            webSocket {
                sendSerialized(info)
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        frame.readText()
                        sendSerialized(info)
                    }
                }
            }
        }
    }
}
