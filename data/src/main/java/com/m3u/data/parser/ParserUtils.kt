package com.m3u.data.parser

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.decodeToSequence
import okhttp3.OkHttpClient
import okhttp3.Request

class ParserUtils(
    val json: Json,
    val okHttpClient: OkHttpClient,
) {
    @OptIn(ExperimentalSerializationApi::class)
    suspend inline fun <reified T> newCall(url: String): T? = withContext(Dispatchers.IO) {
        okHttpClient.newCall(
            Request.Builder().url(url).build()
        )
            .execute()
            .takeIf { it.isSuccessful }
            ?.body
            ?.byteStream()
            ?.let { json.decodeFromStream(it) }
    }

    @OptIn(ExperimentalSerializationApi::class)
    suspend inline fun <reified T> newCallOrThrow(url: String): T =
        withContext(Dispatchers.IO) {
            okHttpClient.newCall(
                Request.Builder().url(url).build()
            )
                .execute()
                .takeIf { it.isSuccessful }!!
                .body!!
                .byteStream()
                .let { json.decodeFromStream(it) }
        }

    @OptIn(ExperimentalSerializationApi::class)
    inline fun <reified T> newSequenceCall(url: String): Sequence<T> = okHttpClient.newCall(
        Request.Builder().url(url).build()
    )
        .execute()
        .takeIf { it.isSuccessful }
        ?.body
        ?.byteStream()
        ?.let { json.decodeToSequence(it) }
        ?: emptySequence()
}