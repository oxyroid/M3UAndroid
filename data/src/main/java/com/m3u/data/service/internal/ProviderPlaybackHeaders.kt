package com.m3u.data.service.internal

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

internal fun OkHttpClient.withProviderPlaybackHeaders(
    entryUrl: String,
    headers: Map<String, String>,
): OkHttpClient {
    if (headers.isEmpty()) return this
    val approvedUrl = entryUrl.toHttpUrl()
    val providerHeaders = headers.toMap()
    return newBuilder()
        // Network interceptors run for every actual request, including redirect hops.
        .addNetworkInterceptor { chain ->
            chain.proceed(
                chain.request().withProviderPlaybackHeaders(
                    approvedUrl = approvedUrl,
                    headers = providerHeaders,
                )
            )
        }
        .build()
}

internal fun Request.withProviderPlaybackHeaders(
    approvedUrl: HttpUrl,
    headers: Map<String, String>,
): Request {
    if (!url.hasSameOriginAs(approvedUrl)) return this
    return newBuilder()
        .apply {
            headers.forEach { (name, value) -> header(name, value) }
        }
        .build()
}

private fun HttpUrl.hasSameOriginAs(other: HttpUrl): Boolean =
    scheme == other.scheme && host == other.host && port == other.port
