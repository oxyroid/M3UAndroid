package com.m3u.data.api

import com.m3u.core.architecture.Publisher
import com.m3u.core.architecture.logger.Logger
import com.m3u.core.architecture.logger.execute
import com.m3u.core.architecture.logger.sandBox
import com.m3u.data.television.http.endpoint.DefRep
import com.m3u.data.television.http.endpoint.SayHello
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
import retrofit2.http.Path
import retrofit2.http.Query
import javax.inject.Inject
import javax.inject.Singleton

interface LocalService {
    @GET("/say_hello")
    suspend fun sayHello(): SayHello.TelevisionInfo?

    @POST("/playlists/subscribe")
    suspend fun subscribe(
        @Query("title") title: String,
        @Query("url") url: String
    ): DefRep?

    @POST("/remotes/{direction}")
    suspend fun remoteDirection(@Path("direction") remoteDirectionValue: Int): DefRep?
}

@Singleton
class LocalPreparedService @Inject constructor(
    private val builder: Retrofit.Builder,
    private val okHttpClient: OkHttpClient,
    @Logger.MessageImpl private val logger: Logger,
    private val publisher: Publisher,
) : LocalService {
    fun prepare(host: String, port: Int): Flow<SayHello.TelevisionInfo> = callbackFlow {
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
            override fun onMessage(webSocket: WebSocket, text: String) = logger.sandBox {
                when (
                    val televisionInfo = json.decodeFromString<SayHello.TelevisionInfo?>(text)
                ) {
                    null -> {}
                    else -> {
                        check(televisionInfo.version == publisher.versionCode) {
                            channel.close()
                            "The software version is incompatible, please make sure the version is consistent."
                        }
                        trySendBlocking(televisionInfo)
                    }
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                channel.close()
            }
        }
        val webSocket = okHttpClient.newWebSocket(request, listener)
        awaitClose {
            api = null
            webSocket.cancel()
        }
    }

    fun close() {
        api = null
    }

    override suspend fun sayHello(): SayHello.TelevisionInfo? = logger.execute {
        requireApi().sayHello()
    }

    override suspend fun subscribe(
        title: String,
        url: String
    ): DefRep? = logger.execute {
        requireApi().subscribe(title, url)
    }

    override suspend fun remoteDirection(remoteDirectionValue: Int): DefRep? = logger.execute {
        requireApi().remoteDirection(remoteDirectionValue)
    }

    private var api: LocalService? = null
    private fun requireApi(): LocalService = checkNotNull(api) { "You haven't connected television" }
}
