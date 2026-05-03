package com.m3u.data.tv.http.endpoint

import com.m3u.data.service.DPadReactionService
import com.m3u.data.tv.model.RemoteDirection
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
data class Remotes @Inject constructor(
    private val dPadReactionService: DPadReactionService
) : Endpoint {
    override fun apply(route: Route) {
        route.route("/remotes") {
            post("{direction?}") {
                val remoteDirection = call
                    .parameters["direction"]
                    ?.toIntOrNull()
                    ?.let(RemoteDirection::of)
                if (remoteDirection == null) {
                    call.respond(
                        DefRep(
                            result = false,
                            reason = "Remote direction is unsupported."
                        )
                    )
                    return@post
                }
                dPadReactionService.emit(remoteDirection)
                call.respond(DefRep(result = true))
            }
        }
    }
}
