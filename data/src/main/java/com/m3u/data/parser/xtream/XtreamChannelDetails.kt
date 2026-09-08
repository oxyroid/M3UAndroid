package com.m3u.data.parser.xtream

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * The descriptive half of a title: what a channel list cannot tell you.
 *
 * Xtream splits this away from the catalogue. get_vod_streams and get_series
 * return names and artwork only, so a synopsis or a cast costs one extra call
 * per title — which is why other clients fetch it when a detail sheet opens
 * rather than up front.
 *
 * Movies and series answer with the same shape under different spellings
 * (`releasedate` against `releaseDate`), hence the aliases below.
 */
@Serializable
data class XtreamChannelDetails(
    @SerialName("info")
    val info: Info? = null,
) {
    @OptIn(ExperimentalSerializationApi::class)
    @Serializable
    data class Info(
        @SerialName("plot")
        @JsonNames("description")
        val plot: String? = null,
        @SerialName("cast")
        @JsonNames("actors")
        val cast: String? = null,
        @SerialName("director")
        val director: String? = null,
        @SerialName("genre")
        val genre: String? = null,
        @SerialName("rating")
        @Serializable(with = LenientStringSerializer::class)
        val rating: String? = null,
        @SerialName("releasedate")
        @JsonNames("releaseDate", "release_date")
        val releaseDate: String? = null,
        @SerialName("duration_secs")
        @Serializable(with = LenientStringSerializer::class)
        val durationSeconds: String? = null,
        @SerialName("tmdb_id")
        @Serializable(with = LenientStringSerializer::class)
        val tmdbId: String? = null,
        @SerialName("o_name")
        val originalName: String? = null,
        @SerialName("movie_image")
        val cover: String? = null,
    )
}

/**
 * Reads a value that panels disagree on the type of.
 *
 * `rating` comes back as 5, as "5", and as 7.4 depending on the server, and
 * `tmdb_id` alternates between a number and a string just as freely. Declaring
 * either as a String makes kotlinx reject the numeric form outright, and the
 * whole detail sheet would be lost over a field nobody reads as a number.
 */
private object LenientStringSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LenientString", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String? {
        val element = (decoder as? JsonDecoder)?.decodeJsonElement()
            ?: return decoder.decodeString()
        if (element is JsonNull) return null
        val primitive = element as? JsonPrimitive ?: return null
        return primitive.content.takeIf { it.isNotBlank() }
    }

    override fun serialize(encoder: Encoder, value: String?) {
        if (value == null) encoder.encodeNull() else encoder.encodeString(value)
    }
}
