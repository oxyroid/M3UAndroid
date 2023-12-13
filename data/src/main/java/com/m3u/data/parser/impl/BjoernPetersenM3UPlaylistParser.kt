package com.m3u.data.parser.impl

import com.m3u.data.parser.M3UPlaylist
import com.m3u.data.parser.M3UPlaylistParser
import com.m3u.data.parser.impl.DefaultM3UPlaylistParser.Companion.M3U_GROUP_TITLE_MARK
import com.m3u.data.parser.impl.DefaultM3UPlaylistParser.Companion.M3U_TVG_ID_MARK
import com.m3u.data.parser.impl.DefaultM3UPlaylistParser.Companion.M3U_TVG_NAME_MARK
import com.m3u.data.parser.model.M3UData
import net.bjoernpetersen.m3u.M3uParser
import net.bjoernpetersen.m3u.model.M3uEntry
import java.io.InputStream
import javax.inject.Inject

class BjoernPetersenM3UPlaylistParser @Inject constructor() : M3UPlaylistParser {
    override val engine: String = "bjoern-petersen"

    override suspend fun execute(input: InputStream): M3UPlaylist {
        val list = input.use {
            M3uParser.parse(it.reader())
        }
        return list.map { it.toM3UData() }
    }

    private fun M3uEntry.toM3UData(): M3UData = M3UData(
        duration = this.duration?.toMillis()?.toDouble() ?: -1.0,
        url = this.location.url.toString(),
        cover = this.metadata.logo.orEmpty(),
        title = this.title.orEmpty(),
        group = this.metadata[M3U_GROUP_TITLE_MARK].orEmpty(),
        id = this.metadata[M3U_TVG_ID_MARK].orEmpty(),
        name = this.metadata[M3U_TVG_NAME_MARK].orEmpty()
    )
}
