package com.m3u.data.remote.parser.impl

import android.net.Uri
import com.m3u.core.util.basic.trimBrackets
import com.m3u.core.util.collection.loadLine
import com.m3u.data.remote.parser.Parser
import com.m3u.data.remote.parser.model.M3UData
import java.io.InputStream
import java.net.URL
import java.util.*

class DefaultPlaylistParser internal constructor() : Parser<List<M3UData>>() {
    override suspend fun execute(stream: InputStream): List<M3UData> = buildList {
        var block: M3UData? = null
        stream
            .bufferedReader()
            .lines()
            .filter { it.isNotEmpty() }
            .forEach { line ->
                when {
                    line.startsWith(M3U_HEADER_MARK) -> block = null
                    line.startsWith(M3U_INFO_MARK) -> {
                        block?.let { add(it) }
                        val decodedContent = Uri.decode(line)
                        block = M3UData().setContent(decodedContent)
                    }
                    line.startsWith("#") -> {}
                    else -> {
                        block = block?.setUrl(line)
                        block?.let {
                            add(it)
                        }
                        block = null
                    }
                }
            }
    }


    private fun M3UData.setUrl(url: String): M3UData = copy(url = url)

    private fun M3UData.setContent(decodedContent: String): M3UData {
        val contents = decodedContent.split(",")
        if (contents.size > 2) {
            error("The content can only have one comma at most! Try decoration before invoking.")
        }
        val spaceContentIndex = contents.indexOfFirst { it.startsWith(M3U_INFO_MARK) }
        val spaceContent = if (spaceContentIndex == -1) null else contents[spaceContentIndex]
        val properties = if (!spaceContent.isNullOrEmpty()) makeProperties(spaceContent)
        else Properties()

        val id = properties.getProperty(M3U_TVG_ID_MARK, "")
        val name = properties.getProperty(M3U_TVG_NAME_MARK, "")
        val cover = properties.getProperty(M3U_TVG_LOGO_MARK, "")

        val group = properties.getProperty(M3U_GROUP_TITLE_MARK, "")
        val title = contents.toMutableList().apply {
            if (spaceContentIndex != -1) {
                removeAt(spaceContentIndex)
            }
        }.firstOrNull().orEmpty()

        val duration = properties.getProperty(M3U_TVG_DURATION, "-1").toLong()

        return this.copy(
            id = id.trimBrackets(),
            name = name.trimBrackets(),
            group = group.trimBrackets(),
            title = title,
            cover = cover.trimBrackets(),
            duration = duration
        )
    }

    private fun makeProperties(spaceContent: String): Properties {
        val properties = Properties()
        val parts = spaceContent.split(" ")
        // check each of parts
        parts
            .mapNotNull { it.trim().ifEmpty { null } }
            .forEach { part ->
                if (part.startsWith(M3U_INFO_MARK)) {
                    val duration = part.drop(M3U_INFO_MARK.length).toLong()
                    properties[M3U_TVG_DURATION] = duration
                } else properties.loadLine(part)
            }
        return properties
    }

    companion object {
        private const val M3U_HEADER_MARK = "#EXTM3U"
        private const val M3U_INFO_MARK = "#EXTINF:"

        private const val M3U_TVG_DURATION = "duration"

        private const val M3U_TVG_LOGO_MARK = "tvg-logo"
        private const val M3U_TVG_ID_MARK = "tvg-id"
        private const val M3U_TVG_NAME_MARK = "tvg-name"
        private const val M3U_GROUP_TITLE_MARK = "group-title"

        fun match(url: String): Boolean = try {
            val path = URL(url).path
            path.endsWith(".m3u", ignoreCase = true) ||
                    path.endsWith(".m3u8", ignoreCase = true)
        } catch (e: Exception) {
            false
        }
    }
}