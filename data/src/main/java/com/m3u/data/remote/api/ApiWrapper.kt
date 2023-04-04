package com.m3u.data.remote.api

import javax.inject.Inject
import retrofit2.Retrofit
import retrofit2.create

abstract class ApiWrapper<API>(
    protected val baseUrl: String
) {
    abstract val api: API
}

class GithubApiWrapper @Inject constructor(
    builder: Retrofit.Builder
) : ApiWrapper<GithubRepositoryApi>("https://api.github.com") {
    override val api: GithubRepositoryApi = builder
        .baseUrl(baseUrl)
        .build()
        .create()
}