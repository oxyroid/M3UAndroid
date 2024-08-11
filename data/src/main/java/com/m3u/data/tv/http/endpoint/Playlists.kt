package com.m3u.data.tv.http.endpoint

import android.content.Context
import androidx.work.WorkManager
import com.m3u.core.architecture.preferences.Preferences
import com.m3u.data.database.model.DataSource
import com.m3u.data.repository.playlist.PlaylistRepository
import com.m3u.data.worker.SubscriptionWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
data class Playlists @Inject constructor(
    private val workManager: WorkManager,
    private val preferences: Preferences,
    private val playlistRepository: PlaylistRepository,
    @ApplicationContext private val context: Context
) : Endpoint {
    override fun apply(route: Route) {
        route.route("/playlists") {
            post("subscribe") {
                val dataSourceValue = call.queryParameters["data_source"]
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

                val title = call.queryParameters["title"]
                val url = call.queryParameters["url"]
                val epg = call.queryParameters["epg"]
                val basicUrl = call.queryParameters["address"]
                val username = call.queryParameters["username"]
                val password = call.queryParameters["password"]

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
                        SubscriptionWorker.m3u(workManager, title, url)
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