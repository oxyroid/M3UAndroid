package com.m3u.data.television.http.endpoint

import com.m3u.core.architecture.logger.Logger
import com.m3u.core.architecture.logger.sandBox
import com.m3u.data.service.RemoteDirectionService
import com.m3u.data.television.model.RemoteDirection
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
data class Remotes @Inject constructor(
    private val logger: Logger,
    private val remoteDirectionService: RemoteDirectionService
) : Endpoint {
    override fun apply(route: Route) {
        route.route("/remotes") {
            post("{direction?}") {
                logger.sandBox {
                    val remoteDirection = call
                        .parameters["direction"]
                        ?.toInt()
                        ?.let { RemoteDirection.of(it) }
                    if (remoteDirection == null) {
                        call.respond(
                            DefRep(
                                result = false,
                                reason = "Remote direction is unsupported."
                            )
                        )
                        return@post
                    }
                    remoteDirectionService.emit(remoteDirection)
                    call.respond(
                        DefRep(result = true)
                    )
                }
            }
        }
    }
}
