package com.m3u.extension.api.workflow

import com.m3u.extension.api.tool.Logger
import com.m3u.extension.api.tool.Saver
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

abstract class Resolver(
    protected val okHttpClient: OkHttpClient,
    protected val json: Json,
    protected val logger: Logger,
    protected val saver: Saver
) {
    abstract suspend fun onResolve(inputs: Map<String, Any>)
}
