@file:Suppress("unused")

package com.m3u.data.api

import com.m3u.data.api.dto.github.File
import com.m3u.data.api.dto.github.Release
import com.m3u.data.api.dto.github.Tree
import com.m3u.data.api.dto.github.User
import retrofit2.http.GET
import retrofit2.http.Path

interface GithubApi {
    @GET("/repos/{author}/{repos}/releases")
    suspend fun releases(
        @Path("author") author: String,
        @Path("repos") repos: String
    ): List<Release>

    @GET("/repos/{author}/{repos}/contents/{subpath}")
    suspend fun contents(
        @Path("author") author: String,
        @Path("repos") repos: String,
        @Path("subpath") subpath: String = ""
    ): File?

    @GET("/repos/{author}/{repos}/trees/{sha}")
    suspend fun tree(
        @Path("author") author: String,
        @Path("repos") repos: String,
        @Path("sha") sha: String
    ): Tree?

    @GET("/repos/{author}/{repos}/contributors")
    suspend fun contributors(
        @Path("author") author: String,
        @Path("repos") repos: String
    ): List<User>
}
