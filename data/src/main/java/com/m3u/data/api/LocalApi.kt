package com.m3u.data.api

import com.m3u.data.local.http.Endpoint
import okhttp3.HttpUrl
import retrofit2.Retrofit
import retrofit2.create
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import javax.inject.Inject

interface LocalApi {
    @GET("/say_hello")
    suspend fun sayHello(): Endpoint.SayHello.Rep?

    @POST("/playlists/subscribe")
    suspend fun subscribe(
        @Query("title") title: String,
        @Query("url") url: String
    ): Endpoint.Playlists.SubscribeRep?
}

class LocalService @Inject constructor(
    private val builder: Retrofit.Builder
) : LocalApi {
    override suspend fun sayHello(): Endpoint.SayHello.Rep? = api?.sayHello()
    override suspend fun subscribe(title: String, url: String): Endpoint.Playlists.SubscribeRep? =
        api?.subscribe(title, url)

    private var api: LocalApi? = null

    fun prepare(host: String, port: Int) {
        val baseUrl = HttpUrl.Builder()
            .host(host)
            .port(port)
            .build()
        api = builder
            .baseUrl(baseUrl)
            .build()
            .create()
    }

    fun close() {
        api = null
    }
}