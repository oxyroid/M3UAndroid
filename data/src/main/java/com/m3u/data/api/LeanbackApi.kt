package com.m3u.data.api

import com.m3u.core.architecture.Publisher
import com.m3u.core.architecture.logger.Logger
import com.m3u.core.architecture.logger.execute
import com.m3u.data.database.model.DataSource
import com.m3u.data.tv.http.endpoint.DefRep
import com.m3u.data.tv.model.TvInfo
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
import retrofit2.http.Path
import retrofit2.http.Query
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
    @OkhttpClient(true) private val okHttpClient: OkHttpClient,
    @Logger.MessageImpl private val logger: Logger,
    private val publisher: Publisher,
): TvApi {
    fun prepare(host: String, port: Int): Flow<TvInfo> = callbackFlow {
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
                try {
                    val info = json.decodeFromString<TvInfo?>(text) ?: return
                    trySendBlocking(info)
                } catch (e: IllegalStateException) {
                    logger.log(e.message.orEmpty())
                    cancel()
                } catch (e: Exception) {
                    logger.log(e.message.orEmpty())
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

    override suspend fun sayHello(): TvInfo? = logger.execute {
        requireApi().sayHello()
    }

    override suspend fun subscribe(
        title: String,
        url: String,
        basicUrl: String,
        username: String,
        password: String,
        epg: String?,
        dataSource: DataSource
    ): DefRep? = logger.execute {
        requireApi().subscribe(title, url, basicUrl, username, password, epg, dataSource)
    }

    override suspend fun remoteDirection(remoteDirectionValue: Int): DefRep? = logger.execute {
        requireApi().remoteDirection(remoteDirectionValue)
    }

    private var api: TvApi? = null
    private fun requireApi(): TvApi =
        checkNotNull(api) { "You haven't connected tv" }

    private fun checkCompatibleInfoOrThrow(info: TvInfo): TvInfo {
        check(info.version == publisher.versionCode) {
            "The software version is incompatible, Please make sure the version is consistent"
        }
        return info
    }
}
