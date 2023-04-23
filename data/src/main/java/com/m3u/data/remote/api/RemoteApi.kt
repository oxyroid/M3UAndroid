@file:Suppress("unused")

package com.m3u.data.remote.api

import com.m3u.data.remote.api.dto.Content
import com.m3u.data.remote.api.dto.Release
import retrofit2.http.GET
import retrofit2.http.Path

interface RemoteApi {
    @GET("/repos/{author}/{repos}/releases")
    suspend fun releases(
        @Path("author") author: String,
        @Path("repos") repos: String
    ): List<Release>

    @GET("/repos/{author}/{repos}/contents")
    suspend fun contents(
        @Path("author") author: String,
        @Path("repos") repos: String
    ): List<Content>

    @GET("/repos/{author}/{repos}/contents/post/{language}")
    suspend fun postContents(
        @Path("author") author: String,
        @Path("repos") repos: String,
        @Path("language") language: String
    ): List<Content>
}
