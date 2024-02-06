package com.m3u.data.local.zmq

import androidx.annotation.Keep
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

internal object ZMQPayloads {
    @Serializable
    object C2S_PAIR_SUB : Message(Type.C2S_PAIR.name)
    @Serializable
    class S2C_PAIR_SUB(val model: String) :
        Message(Type.S2C_PAIR.name, metadata = mapOf("model" to model))

    @Serializable
    object S2C_PAIR_EMPTY : Message(Type.EMPTY.name)

    enum class Type {
        C2S_PAIR, S2C_PAIR, CONTENT, EMPTY
    }

    @Serializable
    @Keep
    internal abstract class Message(
        val type: String,
        val content: String = "",
        val metadata: Map<String, String> = emptyMap()
    ) {
        companion object {
            val json = Json {
                classDiscriminator = "type"
                serializersModule = SerializersModule {
                    polymorphic(Message::class) {
                        subclass(C2S_PAIR_SUB::class)
                        subclass(S2C_PAIR_SUB::class)
                        subclass(S2C_PAIR_EMPTY::class)
                    }
                }
            }
            fun of(s: String): Message = json.decodeFromString<Message>(s)
        }
    }
}

internal fun ZMQPayloads.Message.asBody(): String {
    return ZMQPayloads.Message.json.encodeToString(this)
}