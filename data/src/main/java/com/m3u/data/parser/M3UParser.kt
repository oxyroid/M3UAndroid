package com.m3u.data.parser

import com.m3u.core.util.loadLine
import com.m3u.core.util.trimBrackets
import com.m3u.data.model.M3U
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*
import java.util.stream.Stream
import kotlin.streams.asSequence

class M3UParser internal constructor() : Parser<List<M3U>> {
    @Volatile
    private var block: M3U? = null
    private val list = mutableListOf<M3U>()

    override suspend fun parse(lines: Stream<String>) {
        withContext(Dispatchers.IO) {
            lines
                .asSequence()
                .filterNot { it.isEmpty() }
                .forEach { line ->
                    when {
                        line.startsWith(M3U_HEADER_MARK) -> reset()
                        line.startsWith(M3U_INFO_MARK) -> {
                            block?.let {
                                list.add(it)
                            }
                            block = M3U().setContent(line)
                        }
                        else -> {
                            block = block?.setUrl(line)
                            block?.let { list.add(it) }
                            block = null
                        }
                    }
                }
        }
    }

    override fun get(): List<M3U> = list

    override fun reset() {
        list.clear()
        block = null
    }

    private fun M3U.setUrl(url: String): M3U = copy(url = url)

    private fun M3U.setContent(content: String): M3U {
        fun String.isLegal(): Boolean {
            return startsWith(M3U_TVG_ID_MARK) ||
                    startsWith(M3U_TVG_LOGO_MARK) ||
                    startsWith(M3U_TVG_NAME_MARK) ||
                    startsWith(M3U_GROUP_TITLE_MARK)
        }

        val properties = Properties()

        val parts = content.split(" ")
        // Check each part
        var illegalPart: String? = null

        for (part in parts.reversed()) {
            if (part.startsWith(M3U_INFO_MARK)) continue
            val p = part + illegalPart.orEmpty()
            illegalPart = if (p.isLegal()) {
                properties.loadLine(p)
                null
            } else {
                p + illegalPart.orEmpty()
            }
        }
        if (illegalPart != null) {
            error("illegal m3u content: $content")
        }

        val id = properties.getProperty(M3U_TVG_ID_MARK, "")
        val name = properties.getProperty(M3U_TVG_NAME_MARK, "")
        val logo = properties.getProperty(M3U_TVG_LOGO_MARK, "")

        val (group, title) = properties.getProperty(M3U_GROUP_TITLE_MARK, ",").split(",")

        return this.copy(
            id = id.trimBrackets(),
            name = name.trimBrackets(),
            group = group.trimBrackets(),
            title = title.trimBrackets(),
            logo = logo.trimBrackets()
        )
    }
}

private const val M3U_HEADER_MARK = "#EXTM3U"
private const val M3U_INFO_MARK = "#EXTINF:"

private const val M3U_TVG_LOGO_MARK = "tvg-logo"
private const val M3U_TVG_ID_MARK = "tvg-id"
private const val M3U_TVG_NAME_MARK = "tvg-name"
private const val M3U_GROUP_TITLE_MARK = "group-title"