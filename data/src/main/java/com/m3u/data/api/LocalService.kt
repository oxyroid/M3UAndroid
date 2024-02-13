package com.m3u.data.api

import com.m3u.core.architecture.logger.Logger
import com.m3u.core.architecture.logger.execute
import com.m3u.data.local.http.endpoint.Playlists
import com.m3u.data.local.http.endpoint.SayHello
import okhttp3.HttpUrl
import retrofit2.Retrofit
import retrofit2.create
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import javax.inject.Inject
import javax.inject.Singleton

interface LocalService {
    @GET("/say_hello")
    suspend fun sayHello(): SayHello.Rep?

    @POST("/playlists/subscribe")
    suspend fun subscribe(
        @Query("title") title: String,
        @Query("url") url: String
    ): Playlists.SubscribeRep?
}

@Singleton
class LocalPreparedService @Inject constructor(
    private val builder: Retrofit.Builder,
    @Logger.Message private val logger: Logger
) : LocalService {
    override suspend fun sayHello(): SayHello.Rep? = logger.execute {
        val api = checkNotNull(api) { "You haven't connected television" }
        api.sayHello()
    }
    override suspend fun subscribe(
        title: String,
        url: String
    ): Playlists.SubscribeRep? = logger.execute {
        val api = checkNotNull(api) { "You haven't connected television" }
        api.subscribe(title, url)
    }

    private var api: LocalService? = null

    fun prepare(host: String, port: Int) {
        val baseUrl = HttpUrl.Builder()
            .scheme("http")
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
