package com.m3u.extension.api.tool

import com.m3u.extension.api.workflow.Workflow
import okhttp3.OkHttpClient

/**
 * Extensions don't need to construct this class,
 * just pass it into your [Workflow] primary constructor.
 */
data class OkhttpClientHolder(
    val okHttpClient: OkHttpClient
)