package com.m3u.data.api

import com.m3u.core.architecture.Publisher
import com.m3u.core.architecture.logger.Logger
import com.m3u.core.architecture.logger.execute
import com.m3u.data.television.http.endpoint.Playlists
import com.m3u.data.television.http.endpoint.SayHello
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
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
    private val okHttpClient: OkHttpClient,
    @Logger.MessageImpl private val logger: Logger,
    private val publisher: Publisher,
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

    fun prepare(host: String, port: Int): Flow<SayHello.Rep> = callbackFlow {
        val json = Json {
            ignoreUnknownKeys = true
        }
        val baseUrl = HttpUrl.Builder()
            .scheme("http")
            .host(host)
            .port(port)
            .build()

        api = builder
            .baseUrl(baseUrl)
            .build()
            .create()

        val request = Request.Builder()
            .url(
                baseUrl
                    .newBuilder("say_hello")!!
                    .addQueryParameter("model", publisher.model)
                    .build()
            )
            .build()

        val listener = object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val rep = json.decodeFromString<SayHello.Rep>(text)
                trySendBlocking(rep)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.cancel()
                cancel(reason)
            }
        }
        val webSocket = okHttpClient.newWebSocket(request, listener)
        awaitClose {
            webSocket.cancel()
        }
    }

    fun close() {
        api = null
    }
}
