package com.m3u.data.tv.http.endpoint

import android.content.Context
import androidx.work.WorkManager
import com.m3u.core.architecture.preferences.Preferences
import com.m3u.data.database.model.DataSource
import com.m3u.data.repository.playlist.PlaylistRepository
import com.m3u.data.worker.SubscriptionWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.http.ContentType
import io.ktor.server.request.contentType
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** WorkManager InputData is limited (~10KB); larger M3U content is written to a temp file and path is passed. */
private const val M3U_INPUT_SIZE_LIMIT = 10_000

@Singleton
data class Playlists @Inject constructor(
    private val workManager: WorkManager,
    private val preferences: Preferences,
    private val playlistRepository: PlaylistRepository,
    @ApplicationContext private val appContext: Context
) : Endpoint {
    override fun apply(route: Route) {
        route.route("/playlists") {
            post("subscribe") {
                val params = when {
                    call.request.contentType().match(ContentType.Application.FormUrlEncoded) -> call.receiveParameters()
                    else -> call.request.queryParameters
                }
                val dataSourceValue = params["data_source"]
                val dataSource = dataSourceValue?.let { DataSource.ofOrNull(it) }
                if (dataSource == null) {
                    call.respond(
                        DefRep(
                            result = false,
                            reason = "DataSource $dataSourceValue is unsupported."
                        )
                    )
                    return@post
                }

                val title = params["title"]
                val url = params["url"]
                val epg = params["epg"]
                val basicUrl = params["address"]
                val username = params["username"]
                val password = params["password"]

                when (dataSource) {
                    DataSource.M3U -> {
                        if (title == null || url == null) {
                            call.respond(
                                DefRep(
                                    result = false,
                                    reason = "Both title and url are required."
                                )
                            )
                            return@post
                        }
                        if (title.isBlank() || url.isBlank()) {
                            call.respond(
                                DefRep(
                                    result = false,
                                    reason = "Provide a playlist URL or select an M3U file."
                                )
                            )
                            return@post
                        }
                        if (url.length > M3U_INPUT_SIZE_LIMIT) {
                            val tempFile = java.io.File(appContext.filesDir, "subscribe_${System.currentTimeMillis()}.m3u")
                            tempFile.writeText(url)
                            SubscriptionWorker.m3uWithContentPath(workManager, title, tempFile.absolutePath)
                        } else {
                            SubscriptionWorker.m3u(workManager, title, url)
                        }
                    }

                    DataSource.Xtream -> {
                        if (title == null || url == null) {
                            call.respond(
                                DefRep(
                                    result = false,
                                    reason = "Both title and url are required."
                                )
                            )
                            return@post
                        }
                        SubscriptionWorker.xtream(
                            workManager,
                            title,
                            url,
                            basicUrl.orEmpty(),
                            username.orEmpty(),
                            password.orEmpty()
                        )
                    }

                    DataSource.EPG -> {
                        if (title == null || epg == null) {
                            call.respond(
                                DefRep(
                                    result = false,
                                    reason = "Both title and epg link are required."
                                )
                            )
                            return@post
                        }
                        playlistRepository.insertEpgAsPlaylist(title, epg)
                    }

                    else -> {}
                }
                call.respond(
                    DefRep(result = true)
                )
            }
        }
    }
}