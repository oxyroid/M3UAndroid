package com.m3u.extension.api.workflow

interface Resolver {
    suspend fun onResolve(inputs: Map<String, Any>)
}
