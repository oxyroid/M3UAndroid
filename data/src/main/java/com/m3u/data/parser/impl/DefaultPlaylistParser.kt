package com.m3u.data.parser.impl

import android.net.Uri
import com.m3u.core.architecture.Logger
import com.m3u.core.util.basic.splitOutOfQuotation
import com.m3u.core.util.basic.trimBrackets
import com.m3u.core.util.loadLine
import com.m3u.data.parser.Playlist
import com.m3u.data.parser.PlaylistParser
import com.m3u.data.parser.model.M3UData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.Properties
import javax.inject.Inject

class DefaultPlaylistParser @Inject constructor(
    private val logger: Logger
) : PlaylistParser {
    override val engine: String = "default"

    private val pattern = Regex("#EXTINF:-?\\d+,")

    override suspend fun execute(input: InputStream): Playlist = buildList {
        withContext(Dispatchers.IO) {
            var block: M3UData? = null
            input
                .bufferedReader()
                .lines()
                .filter { it.isNotEmpty() }
                .forEach { line ->
                    when {
                        line.startsWith(M3U_HEADER_MARK) -> block = null
                        line.startsWith(M3U_INFO_MARK) -> {
                            try {
                                block?.let { add(it) }
                                // decoded content and replace "#EXTINF:-1," to "#EXTINF:-1 "
                                val decodedContent = pattern.replace(
                                    Uri.decode(line)
                                ) { result ->
                                    result.value.dropLast(1) + " "
                                }
                                block = M3UData().setContent(decodedContent)
                            } catch (e: Exception) {
                                logger.log(e)
                            }
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
    }

    private fun M3UData.setUrl(url: String): M3UData = run {
        // if (URI.create(url).scheme == null) throwConfusingFormatError()
        copy(url = url)
    }

    private fun M3UData.setContent(decodedContent: String): M3UData {
        val contents = decodedContent.splitOutOfQuotation(',')
        val spaceContentIndex =
            contents.indexOfFirst { it.startsWith(M3U_INFO_MARK) }
        val spaceContent = if (spaceContentIndex == -1) null else contents[spaceContentIndex]
        val properties = if (!spaceContent.isNullOrEmpty()) makeProperties(spaceContent)
        else Properties()

        val id = properties.getProperty(
            M3U_TVG_ID_MARK,
            ""
        )
        val name = properties.getProperty(
            M3U_TVG_NAME_MARK,
            ""
        )
        val cover = properties.getProperty(
            M3U_TVG_LOGO_MARK,
            ""
        )
        val group = properties.getProperty(
            M3U_GROUP_TITLE_MARK,
            ""
        )
        val title = contents.toMutableList().apply {
            if (spaceContentIndex != -1) {
                removeAt(spaceContentIndex)
            }
        }.firstOrNull().orEmpty()
        val duration = properties.getProperty(
            M3U_TVG_DURATION,
            "-1"
        ).toDouble()

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
        val parts = spaceContent.splitOutOfQuotation(' ')
        // check each of parts
        parts
            .mapNotNull { it.trim().ifEmpty { null } }
            .forEach { part ->
                if (part.startsWith(M3U_INFO_MARK)) {
                    val duration = part.drop(M3U_INFO_MARK.length).toDouble()
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
        const val M3U_TVG_ID_MARK = "tvg-id"
        const val M3U_TVG_NAME_MARK = "tvg-name"
        const val M3U_GROUP_TITLE_MARK = "group-title"
    }
}
