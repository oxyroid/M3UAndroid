package com.m3u.data.service.internal

import java.io.IOException
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

internal fun OkHttpClient.withProviderPlaybackHeaders(
    entryUrl: String,
    headers: Map<String, String>,
    allowCrossOriginRequests: Boolean,
): OkHttpClient {
    if (headers.isEmpty() && allowCrossOriginRequests) return this
    val approvedUrl = entryUrl.toHttpUrl()
    val providerHeaders = headers.toMap()
    val builder = newBuilder()
    if (!allowCrossOriginRequests) {
        builder
            .followRedirects(false)
            .followSslRedirects(false)
            // Application interceptors run before DNS and connection establishment.
            .addInterceptor { chain ->
                var request = chain.request().withProviderPlaybackHeaders(
                    approvedUrl = approvedUrl,
                    headers = providerHeaders,
                    allowCrossOriginRequests = false,
                )
                repeat(MAX_STRICT_REDIRECTS + 1) { redirectCount ->
                    val response = chain.proceed(request)
                    if (!response.isRedirect) return@addInterceptor response
                    val target = response.header("Location")
                        ?.let(response.request.url::resolve)
                        ?: return@addInterceptor response
                    response.close()
                    if (redirectCount == MAX_STRICT_REDIRECTS) {
                        throw IOException("External provider playback redirected too many times")
                    }
                    request = request.newBuilder()
                        .url(target)
                        .build()
                        .withProviderPlaybackHeaders(
                            approvedUrl = approvedUrl,
                            headers = providerHeaders,
                            allowCrossOriginRequests = false,
                        )
                }
                error("Strict playback redirect loop terminated unexpectedly")
            }
    }
    return builder
        // Network interceptors run for every actual request, including redirect hops.
        .addNetworkInterceptor { chain ->
            chain.proceed(
                chain.request().withProviderPlaybackHeaders(
                    approvedUrl = approvedUrl,
                    headers = providerHeaders,
                    allowCrossOriginRequests = allowCrossOriginRequests,
                )
            )
        }
        .build()
}

internal fun Request.withProviderPlaybackHeaders(
    approvedUrl: HttpUrl,
    headers: Map<String, String>,
    allowCrossOriginRequests: Boolean = true,
): Request {
    if (!url.hasSameOriginAs(approvedUrl)) {
        if (!allowCrossOriginRequests) {
            throw IOException("External provider playback cannot leave its approved origin")
        }
        val sanitized = newBuilder()
        headers.forEach { (name, providerValue) ->
            if (header(name) == providerValue) {
                sanitized.removeHeader(name)
            }
        }
        return sanitized.build()
    }
    return newBuilder()
        .apply {
            headers.forEach { (name, value) -> header(name, value) }
        }
        .build()
}

private fun HttpUrl.hasSameOriginAs(other: HttpUrl): Boolean =
    scheme == other.scheme && host == other.host && port == other.port

private const val MAX_STRICT_REDIRECTS = 5
