package com.m3u.data.parser.epg

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.InputStream
import javax.inject.Inject

internal class EpgParserImpl @Inject constructor(
) : EpgParser {
    override fun readProgrammes(input: InputStream): Flow<EpgProgramme> {
        val buffered = input.buffered()
        buffered.skipUtf8Bom()
        return if (buffered.firstNonWhitespaceByte() in JSON_START_BYTES) {
            readJsonProgrammes(buffered)
        } else {
            readXmlProgrammes(buffered)
        }
    }

    private fun readXmlProgrammes(input: InputStream): Flow<EpgProgramme> = channelFlow {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)
        val channelAliases = mutableMapOf<String, List<String>>()
        with(parser) {
            while (name != "tv") next()
            while (next() != XmlPullParser.END_TAG) {
                if (eventType != XmlPullParser.START_TAG) continue
                when (name) {
                    "channel" -> {
                        val channel = readChannel()
                        channelAliases[channel.id] = channel.aliases
                    }

                    "programme" -> {
                        val programme = readProgramme(channelAliases)
                        send(programme)
                    }

                    else -> skip()
                }
            }
        }
    }
        .flowOn(Dispatchers.Default)

    @OptIn(ExperimentalSerializationApi::class)
    private fun readJsonProgrammes(input: InputStream): Flow<EpgProgramme> = channelFlow {
        val objects = json.decodeFromStream<JsonElement>(input)
            .programmeObjects()
        objects.forEachIndexed { index, (programme, channel) ->
            val fallbackStop = objects
                .getOrNull(index + 1)
                ?.first
                ?.takeIf { next -> programme.canInferStopFrom(next) }
                ?.stringValue(*JSON_START_KEYS)
            programme.toEpgProgramme(channel, fallbackStop)?.let { send(it) }
        }
    }
        .flowOn(Dispatchers.Default)

    private val ns: String? = null
    private val json = Json { ignoreUnknownKeys = true }

    private data class EpgChannel(
        val id: String,
        val aliases: List<String>
    )

    private fun XmlPullParser.readChannel(): EpgChannel {
        require(XmlPullParser.START_TAG, ns, "channel")
        val id = getAttributeValue(null, "id").orEmpty()
        val aliases = mutableListOf<String>()
        while (next() != XmlPullParser.END_TAG) {
            if (eventType != XmlPullParser.START_TAG) continue
            when (name) {
                "display-name" -> aliases += readDisplayName()
                else -> skip()
            }
        }
        require(XmlPullParser.END_TAG, ns, "channel")
        return EpgChannel(
            id = id,
            aliases = aliases.filter { it.isNotBlank() }.distinct()
        )
    }

    private fun XmlPullParser.readProgramme(
        channelAliases: Map<String, List<String>>
    ): EpgProgramme {
        require(XmlPullParser.START_TAG, ns, "programme")
        val start = getAttributeValue(null, "start")
        val stop = getAttributeValue(null, "stop")
        val channel = getAttributeValue(null, "channel")
        var title: String? = null
        var desc: String? = null
        val categories = mutableListOf<String>()
        var icon: String? = null
        while (next() != XmlPullParser.END_TAG) {
            if (eventType != XmlPullParser.START_TAG) continue
            when (name) {
                "title" -> title = readTitle()
                "desc" -> desc = readDesc()
                "category" -> categories += readCategory()
                "icon" -> icon = readIcon()
                else -> skip()
            }
        }
        require(XmlPullParser.END_TAG, ns, "programme")
        return EpgProgramme(
            start = start,
            stop = stop,
            channel = channel,
            channelAliases = channelAliases[channel].orEmpty(),
            title = title,
            desc = desc,
            icon = icon,
            categories = categories
        )
    }

    private fun XmlPullParser.skip() {
        check(eventType == XmlPullParser.START_TAG)
        var depth = 1
        while (depth != 0) {
            when (next()) {
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.START_TAG -> depth++
            }
        }
    }

    private fun XmlPullParser.readIcon(): String? = optional {
        require(XmlPullParser.START_TAG, ns, "icon")
        val icon = getAttributeValue(null, "src")
        nextTag()
        require(XmlPullParser.END_TAG, ns, "icon")
        return icon
    }

    private fun XmlPullParser.readTitle(): String? = optional {
        require(XmlPullParser.START_TAG, ns, "title")
        val title = readText()
        require(XmlPullParser.END_TAG, ns, "title")
        return title
    }

    private fun XmlPullParser.readDisplayName(): String {
        require(XmlPullParser.START_TAG, ns, "display-name")
        val displayName = readText()
        require(XmlPullParser.END_TAG, ns, "display-name")
        return displayName
    }

    private fun XmlPullParser.readDesc(): String? = optional {
        require(XmlPullParser.START_TAG, ns, "desc")
        val desc = readText()
        require(XmlPullParser.END_TAG, ns, "desc")
        return desc
    }

    private fun XmlPullParser.readCategory(): String {
        require(XmlPullParser.START_TAG, ns, "category")
        val category = readText()
        require(XmlPullParser.END_TAG, ns, "category")
        return category
    }

    private fun XmlPullParser.readText(): String {
        var result = ""
        if (next() == XmlPullParser.TEXT) {
            result = text
            nextTag()
        }
        return result
    }

    private inline fun optional(block: () -> String): String? =
        runCatching { block() }
            .getOrNull()

    private fun JsonElement.programmeObjects(): List<Pair<JsonObject, JsonObject?>> = when (this) {
        is JsonArray -> mapNotNull { element ->
            element.jsonObjectOrNull()?.let { it to null }
        }

        is JsonObject -> {
            val defaultChannel = readChannelObject() ?: readRootChannelObject()
            val array = firstArray(
                "programmes",
                "programs",
                "programme",
                "program",
                "epg",
                "epg_list",
                "epg_data",
                "events",
                "data",
                "list"
            )
            when {
                array != null -> array.mapNotNull { element ->
                    element.jsonObjectOrNull()?.let { it to defaultChannel }
                }

                looksLikeProgramme() -> listOf(this to defaultChannel)
                else -> emptyList()
            }
        }

        else -> emptyList()
    }

    private fun JsonObject.toEpgProgramme(
        defaultChannel: JsonObject?,
        fallbackStop: String?
    ): EpgProgramme? {
        val nestedChannel = readChannelObject()
        val channelObject = nestedChannel ?: defaultChannel
        val channel = stringValue("channel", "channel_id", "channelId", "channelid", "id")
            ?: channelObject?.stringValue("id", "channel_id", "channelId", "channelid")
            ?: channelObject?.stringValue("name", "title", "display_name", "displayName")
            ?: return null
        val title = stringValue("title", "program_title", "programme_title", "name")
        val desc = stringValue("desc", "description", "summary", "sub_title", "subtitle")
        return EpgProgramme(
            channel = channel,
            channelAliases = buildList {
                stringValue("channel_name", "channelName", "display_name", "displayName")?.let(::add)
                channelObject?.stringValue("name", "title", "display_name", "displayName")?.let(::add)
            }.filter { it.isNotBlank() }.distinct(),
            start = stringValue(*JSON_START_KEYS),
            stop = stringValue(*JSON_STOP_KEYS) ?: fallbackStop,
            title = title,
            desc = desc,
            icon = stringValue("icon", "image", "poster") ?: this["icon"]?.jsonObjectOrNull()?.stringValue("src"),
            categories = categories()
        )
    }

    private fun JsonObject.readChannelObject(): JsonObject? =
        firstObject("channel", "station")

    private fun JsonObject.readRootChannelObject(): JsonObject? =
        takeIf {
            stringValue("id", "channel_id", "channelId", "channelid") != null ||
                    stringValue("name", "title", "display_name", "displayName") != null
        }

    private fun JsonObject.canInferStopFrom(next: JsonObject): Boolean {
        val channel = stringValue("channel", "channel_id", "channelId", "channelid", "id")
        val nextChannel = next.stringValue("channel", "channel_id", "channelId", "channelid", "id")
        return channel == null || nextChannel == null || channel == nextChannel
    }

    private fun JsonObject.looksLikeProgramme(): Boolean =
        stringValue(*JSON_START_KEYS) != null &&
                stringValue("title", "program_title", "programme_title", "name") != null

    private fun JsonObject.categories(): List<String> {
        val values = mutableListOf<String>()
        stringValue("category", "genre")?.let(values::add)
        listOf("categories", "genres").forEach { key ->
            val array = this[key]?.jsonArrayOrNull() ?: return@forEach
            array.mapNotNullTo(values) { it.stringOrNull() }
        }
        return values.filter { it.isNotBlank() }.distinct()
    }

    private fun JsonObject.stringValue(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key -> this[key]?.stringOrNull()?.takeIf { it.isNotBlank() } }

    private fun JsonObject.firstArray(vararg keys: String): JsonArray? =
        keys.firstNotNullOfOrNull { key -> this[key]?.jsonArrayOrNull() }

    private fun JsonObject.firstObject(vararg keys: String): JsonObject? =
        keys.firstNotNullOfOrNull { key -> this[key]?.jsonObjectOrNull() }

    private fun JsonElement.jsonObjectOrNull(): JsonObject? = this as? JsonObject

    private fun JsonElement.jsonArrayOrNull(): JsonArray? = this as? JsonArray

    private fun JsonElement.stringOrNull(): String? = when (this) {
        JsonNull -> null
        is JsonPrimitive -> jsonPrimitive.contentOrNull
        else -> null
    }

    private fun InputStream.buffered(): BufferedInputStream =
        if (this is BufferedInputStream) this else BufferedInputStream(this)

    private fun BufferedInputStream.skipUtf8Bom() {
        mark(UTF8_BOM_BYTES.size)
        val bytes = ByteArray(UTF8_BOM_BYTES.size)
        val read = read(bytes)
        if (read != UTF8_BOM_BYTES.size || !bytes.contentEquals(UTF8_BOM_BYTES)) {
            reset()
        }
    }

    private fun BufferedInputStream.firstNonWhitespaceByte(): Int {
        mark(1024)
        var value = read()
        while (value != -1 && value.toChar().isWhitespace()) {
            value = read()
        }
        reset()
        return value
    }

    private companion object {
        val JSON_START_BYTES = setOf('{'.code, '['.code)
        val JSON_START_KEYS = arrayOf(
            "start",
            "start_time",
            "startTime",
            "start_timestamp",
            "startTimestamp",
            "start_date",
            "startDate",
            "begin",
            "begin_time",
            "beginTime"
        )
        val JSON_STOP_KEYS = arrayOf(
            "stop",
            "stop_time",
            "stopTime",
            "stop_timestamp",
            "stopTimestamp",
            "stop_date",
            "stopDate",
            "end",
            "end_time",
            "endTime",
            "end_timestamp",
            "endTimestamp",
            "end_date",
            "endDate",
            "finish",
            "finish_time",
            "finishTime"
        )
        val UTF8_BOM_BYTES = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    }
}
