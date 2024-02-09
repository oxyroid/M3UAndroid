package com.m3u.data.local.endpoint.internal

import io.javalin.json.JsonMapper
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.serializer
import java.io.InputStream
import java.lang.reflect.Type

@Suppress("UNCHECKED_CAST")
@OptIn(ExperimentalSerializationApi::class)
internal class KotlinxSerializationJsonMapper(private val json: Json) : JsonMapper {
    override fun <T : Any> fromJsonString(json: String, targetType: Type): T {
        return this.json.decodeFromString(
            this.json.serializersModule.serializer(targetType),
            json
        ) as T
    }

    override fun <T : Any> fromJsonStream(json: InputStream, targetType: Type): T {
        return this.json.decodeFromStream(
            this.json.serializersModule.serializer(targetType),
            json
        ) as T
    }

    override fun toJsonString(obj: Any, type: Type): String {
        return this.json.encodeToString(
            this.json.serializersModule.serializer(type),
            obj
        )
    }
}
