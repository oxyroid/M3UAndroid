package com.m3u.data.local.http

import androidx.annotation.Keep
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.m3u.core.architecture.Publisher
import com.m3u.core.architecture.pref.Pref
import com.m3u.data.work.SubscriptionWorker
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import javax.inject.Inject

sealed interface Endpoint {
    fun apply(route: Route)
    data class SayHello @Inject constructor(
        private val publisher: Publisher
    ) : Endpoint {
        override fun apply(route: Route) {
            route.route("/say_hello") {
                get {
                    val rep = Rep(
                        model = publisher.model,
                        version = publisher.versionCode,
                        snapshot = publisher.snapshot
                    )
                    call.respond(rep)
                }
            }
        }

        @Keep
        @Serializable
        data class Rep(
            val model: String,
            val version: Int,
            val snapshot: Boolean
        )
    }

    data class Playlists @Inject constructor(
        private val workManager: WorkManager,
        private val pref: Pref
    ) : Endpoint {
        override fun apply(route: Route) {
            route.route("/playlists") {
                post("/subscribe") {
                    val title = call.queryParameters["title"]
                    val url = call.queryParameters["url"]
                    if (title == null || url == null) {
                        call.respond(
                            SubscribeRep(success = false)
                        )
                        return@post
                    }
                    workManager.cancelAllWorkByTag(url)

                    val request = OneTimeWorkRequestBuilder<SubscriptionWorker>()
                        .setInputData(
                            workDataOf(
                                SubscriptionWorker.INPUT_STRING_TITLE to title,
                                SubscriptionWorker.INPUT_STRING_URL to url,
                                SubscriptionWorker.INPUT_INT_STRATEGY to pref.playlistStrategy
                            )
                        )
                        .addTag(url)
                        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                        .build()
                    workManager.enqueue(request)

                    call.respond(
                        SubscribeRep(success = true)
                    )
                }
            }
        }

        @Keep
        @Serializable
        data class SubscribeRep(
            val success: Boolean
        )
    }
}
