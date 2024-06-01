package com.m3u.data.parser

import com.m3u.core.architecture.logger.Logger
import com.m3u.core.architecture.logger.execute
import kotlinx.coroutines.CoroutineDispatcher
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
    val logger: Logger,
    val ioDispatcher: CoroutineDispatcher
) {
    @OptIn(ExperimentalSerializationApi::class)
    suspend inline fun <reified T> newCall(url: String): T? = withContext(ioDispatcher) {
        logger.execute {
            okHttpClient.newCall(
                Request.Builder().url(url).build()
            )
                .execute()
                .takeIf { it.isSuccessful }
                ?.body
                ?.byteStream()
                ?.let { json.decodeFromStream(it) }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    suspend inline fun <reified T> newCallOrThrow(url: String): T =
        withContext(ioDispatcher) {
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
    inline fun <reified T> newSequenceCall(url: String): Sequence<T> =
        logger.execute {
            okHttpClient.newCall(
                Request.Builder().url(url).build()
            )
                .execute()
                .takeIf { it.isSuccessful }
                ?.body
                ?.byteStream()
                ?.let { json.decodeToSequence(it) }
        } ?: sequence { }
}