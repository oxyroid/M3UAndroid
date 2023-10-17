package com.m3u.data.api

import retrofit2.http.POST

internal interface DropboxApi {
    // limitation: 150MB
    @POST("files/upload")
    suspend fun upload()
}