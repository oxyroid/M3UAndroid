package com.m3u.data.remote.api

import javax.inject.Inject
import retrofit2.Retrofit
import retrofit2.create

abstract class Client<T> {
    protected abstract val baseUrl: String
    abstract val api: T
}

class GithubClient @Inject constructor(
    builder: Retrofit.Builder
) : Client<GithubApi>() {
    override val baseUrl: String = "https://api.github.com"
    override val api: GithubApi = builder
        .baseUrl(baseUrl)
        .build()
        .create()
}