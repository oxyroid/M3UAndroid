package com.m3u.data.parser.epg

import android.util.Xml
import com.m3u.core.architecture.logger.Logger
import com.m3u.core.architecture.logger.Profiles
import com.m3u.core.architecture.logger.install
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import javax.inject.Inject

class EpgParserImpl @Inject constructor(
    delegate: Logger
) : EpgParser {
    private val logger = delegate.install(Profiles.PARSER_EPG)
    override suspend fun execute(
        input: InputStream,
        callback: (count: Int, total: Int) -> Unit
    ): EpgData {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)
        return parser.readEpg()
    }

    private val ns: String? = null
    private fun XmlPullParser.readEpg(): EpgData {
        while (name != "tv") next()
        val channels = mutableListOf<EpgChannel>()
        val programmes = mutableListOf<EpgProgramme>()
        while (next() != XmlPullParser.END_TAG) {
            if (eventType != XmlPullParser.START_TAG) continue
            when (name) {
                "channel" -> channels += readChannel()
                "programme" -> programmes += readProgramme()
                else -> skip()
            }
        }
        logger.log("complete: channel+${channels.size}, programme+${programmes.size}")
        return EpgData(
            channels = channels,
            programmes = programmes
        )
    }

    private fun XmlPullParser.readChannel(): EpgChannel {
        require(XmlPullParser.START_TAG, ns, "channel")
        val id = getAttributeValue(null, "id")
        var displayName: String? = null
        var icon: String? = null
        var url: String? = null
        while (next() != XmlPullParser.END_TAG) {
            if (eventType != XmlPullParser.START_TAG) continue
            when (name) {
                "display-name" -> displayName = readDisplayName()
                "icon" -> icon = readIcon()
                "url" -> url = readUrl()
                else -> skip()
            }
        }
        require(XmlPullParser.END_TAG, ns, "channel")
        return EpgChannel(
            id = id,
            displayName = displayName,
            icon = icon,
            url = url
        )
    }

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

    private fun XmlPullParser.readDisplayName(): String? = optional {
        require(XmlPullParser.START_TAG, ns, "display-name")
        val displayName = readText()
        require(XmlPullParser.END_TAG, ns, "display-name")
        return displayName
    }

    private fun XmlPullParser.readIcon(): String? = optional {
        require(XmlPullParser.START_TAG, ns, "icon")
        val icon = getAttributeValue(null, "src")
        nextTag()
        require(XmlPullParser.END_TAG, ns, "icon")
        return icon
    }

    private fun XmlPullParser.readUrl(): String? = optional {
        require(XmlPullParser.START_TAG, ns, "url")
        val url = readText()
        require(XmlPullParser.END_TAG, ns, "url")
        return url
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
            .onFailure { logger.log(it) }
            .getOrNull()
}