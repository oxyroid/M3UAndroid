package com.m3u.data.remote.api

import com.m3u.data.local.entity.Release
import retrofit2.http.GET
import retrofit2.http.Path

interface GithubApi {
    @GET("/repos/{author}/{repos}/releases")
    suspend fun getReleases(
        @Path("author") author: String,
        @Path("repos") repos: String
    ): List<Release>
}