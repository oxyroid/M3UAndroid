package com.m3u.data.television.http.endpoint

import io.ktor.server.routing.Route

sealed interface Endpoint {
    fun apply(route: Route)
}
