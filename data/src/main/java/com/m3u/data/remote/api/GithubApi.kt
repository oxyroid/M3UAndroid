package com.m3u.data.remote.api

import com.m3u.data.database.entity.Release
import retrofit2.http.GET
import retrofit2.http.Path

interface GithubRepositoryApi {
    @GET("/repos/{author}/{repos}/releases")
    suspend fun releases(
        @Path("author") author: String,
        @Path("repos") repos: String
    ): List<Release>
}
