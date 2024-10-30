package com.m3u.extension.api.runner

import okhttp3.OkHttpClient

abstract class ClientRunner(name: String) : Runner(name) {
    abstract val client: OkHttpClient
}