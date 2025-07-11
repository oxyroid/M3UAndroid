package com.m3u.data.parser.epg

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import javax.inject.Inject

internal class EpgParserImpl @Inject constructor(
) : EpgParser {
    override fun readProgrammes(input: InputStream): Flow<EpgProgramme> = channelFlow {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)
        with(parser) {
            while (name != "tv") next()
            while (next() != XmlPullParser.END_TAG) {
                if (eventType != XmlPullParser.START_TAG) continue
                when (name) {
                    "programme" -> {
                        val programme = readProgramme()
                        send(programme)
                    }

                    else -> skip()
                }
            }
        }
    }
        .flowOn(Dispatchers.Default)

    private val ns: String? = null
    private fun XmlPullParser.readProgramme(): EpgProgramme {
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
}