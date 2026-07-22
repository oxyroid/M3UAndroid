package com.m3u.data.extension.security

import okhttp3.HttpUrl.Companion.toHttpUrl

internal fun String.toCanonicalHttpOrigin(): String {
    val url = toHttpUrl()
    require(url.scheme == "http" || url.scheme == "https") {
        "Provider origin must use HTTP or HTTPS"
    }
    require(url.username.isEmpty() && url.password.isEmpty()) {
        "Provider URL must not contain user information"
    }
    val host = url.host.let { value -> if (':' in value) "[$value]" else value }
    return "${url.scheme}://$host:${url.port}"
}
