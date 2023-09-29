package com.m3u.data.remote.parser.m3u

import android.content.Context
import com.m3u.core.architecture.logger.Logger
import com.m3u.core.util.basic.splitOutOfQuotation
import com.m3u.core.util.basic.trimBrackets
import com.m3u.core.util.loadLine
import com.m3u.data.remote.parser.Parser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.stream.consumeAsFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.net.URI
import java.net.URLDecoder
import java.util.Properties


interface StreamParser : Parser<InputStream, Flow<M3UData>>

class ConcurrentStreamParser(
    private val context: Context,
    private val logger: Logger
) : StreamParser {
    private val pattern = Regex("#EXTINF:-?\\d+,")

    override suspend fun execute(input: InputStream): Flow<M3UData> = channelFlow {
        withContext(Dispatchers.IO) {
            val file = download(input) ?: return@withContext
            var info: String? = null
            file
                .bufferedReader()
                .lines()
                .consumeAsFlow()
                .onEach { line ->
                    info?.let {
                        parse(it, line)?.let { element -> send(element) }
                    } ?: run {
                        info = line
                    }
                }
        }

    }

    private fun download(input: InputStream): File? {
        return try {
            File(
                context.cacheDir,
                "CPL_${System.currentTimeMillis()}"
            ).apply {
                delete()
                createNewFile()
                writeText(input.bufferedReader().readText())
            }
        } catch (e: Exception) {
            logger.log(e)
            null
        }
    }

    private fun parse(info: String, url: String): M3UData? {
        return try {
            // decoded content and replace "#EXTINF:-1," to "#EXTINF:-1 "
            val decodedContent = pattern.replace(
                URLDecoder.decode(info)
            ) { result ->
                result.value.dropLast(1) + " "
            }
            return M3UData()
                .setContent(decodedContent)
                .setUrl(url)
        } catch (e: Exception) {
            logger.log(e)
            null
        }
    }


    @Throws(InvalidatePlaylistError::class)
    private fun M3UData.setUrl(url: String): M3UData = run {
        if (URI.create(url).scheme == null) throw InvalidatePlaylistError
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
        private const val M3U_TVG_ID_MARK = "tvg-id"
        private const val M3U_TVG_NAME_MARK = "tvg-name"
        private const val M3U_GROUP_TITLE_MARK = "group-title"
    }
}