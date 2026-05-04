package com.m3u.data.api

import com.m3u.core.foundation.architecture.Publisher
import com.m3u.data.database.model.DataSource
import com.m3u.data.tv.http.endpoint.DefRep
import com.m3u.data.tv.model.TvInfo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import retrofit2.Retrofit
import retrofit2.create
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

interface TvApi {
    @GET("/say_hello")
    suspend fun sayHello(): TvInfo?

    @POST("/playlists/subscribe")
    suspend fun subscribe(
        @Query("title") title: String,
        @Query("url") url: String,
        @Query("address") basicUrl: String,
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("epg") epg: String?,
        @Query("data_source") dataSource: DataSource
    ): DefRep?

    @POST("/remotes/{direction}")
    suspend fun remoteDirection(@Path("direction") remoteDirectionValue: Int): DefRep?
}

@Singleton
class TvApiDelegate @Inject constructor(
    private val builder: Retrofit.Builder,
    @param:OkhttpClient(true) private val okHttpClient: OkHttpClient,
    private val publisher: Publisher
) : TvApi {
    private var api: TvApi? = null
    private val timber = Timber.tag("TvApiDelegate")

    fun prepare(host: String, port: Int): Flow<TvInfo> = callbackFlow {
        val json = Json { ignoreUnknownKeys = true }
        timber.d("prepare start, host=$host, port=$port")
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
            override fun onOpen(webSocket: WebSocket, response: Response) {
                timber.d("say_hello websocket opened, code=${response.code}")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                timber.d("say_hello websocket message: $text")
                runCatching { json.decodeFromString<TvInfo?>(text) }
                    .getOrNull()
                    ?.let { trySend(it) }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                timber.d("say_hello websocket closing, code=$code, reason=$reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                timber.d("say_hello websocket closed, code=$code, reason=$reason")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                timber.e(t, "say_hello websocket failed, code=${response?.code}")
            }
        }
        val webSocket = okHttpClient.newWebSocket(request, listener)
        awaitClose {
            timber.d("prepare close")
            webSocket.cancel()
        }
    }

    fun close() {
        timber.d("close")
        api = null
    }

    override suspend fun sayHello(): TvInfo? = requireApi().sayHello()

    override suspend fun subscribe(
        title: String,
        url: String,
        basicUrl: String,
        username: String,
        password: String,
        epg: String?,
        dataSource: DataSource
    ): DefRep? = requireApi().subscribe(title, url, basicUrl, username, password, epg, dataSource)

    override suspend fun remoteDirection(remoteDirectionValue: Int): DefRep? =
        requireApi().remoteDirection(remoteDirectionValue)

    private fun requireApi(): TvApi = checkNotNull(api) { "You haven't connected tv" }
}
