package com.m3u.data.local.endpoint

import androidx.annotation.Keep
import com.m3u.core.architecture.Publisher
import io.javalin.http.Context
import io.javalin.http.HandlerType
import kotlinx.serialization.Serializable
import javax.inject.Inject

sealed class Endpoint(val type: HandlerType, val path: String) {
    abstract operator fun invoke(context: Context)
    data class SayHello @Inject constructor(
        private val publisher: Publisher
    ) : Endpoint(HandlerType.GET, "/say_hello") {
        override fun invoke(context: Context) {
            val rep = SayHelloRep(
                model = publisher.model,
                version = publisher.versionCode,
                snapshot = publisher.snapshot
            )
            context.json(rep)
        }

        @Keep
        @Serializable
        data class SayHelloRep(
            val model: String,
            val version: Int,
            val snapshot: Boolean
        )
    }
}
