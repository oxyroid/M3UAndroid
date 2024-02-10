package com.m3u.data.local.http.endpoint

import io.ktor.server.routing.Route

sealed interface Endpoint {
    fun apply(route: Route)
}
