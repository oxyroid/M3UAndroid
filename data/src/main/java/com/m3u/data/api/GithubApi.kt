@file:Suppress("unused")

package com.m3u.data.api

import com.m3u.data.api.dto.User
import com.m3u.data.api.dto.File
import com.m3u.data.api.dto.Release
import retrofit2.http.GET
import retrofit2.http.Path

interface GithubApi {
    @GET("/repos/{author}/{repos}/releases")
    suspend fun releases(
        @Path("author") author: String,
        @Path("repos") repos: String
    ): List<Release>

    @GET("/repos/{author}/{repos}/contents")
    suspend fun contents(
        @Path("author") author: String,
        @Path("repos") repos: String
    ): List<File>

    @GET("/repos/{author}/{repos}/contributors")
    suspend fun contributors(
        @Path("author") author: String,
        @Path("repos") repos: String
    ): List<User>
}
