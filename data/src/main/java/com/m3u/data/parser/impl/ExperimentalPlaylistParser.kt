package com.m3u.data.parser.impl

import com.m3u.data.parser.PlaylistParser
import com.m3u.data.parser.impl.DefaultPlaylistParser.Companion.M3U_GROUP_TITLE_MARK
import com.m3u.data.parser.impl.DefaultPlaylistParser.Companion.M3U_TVG_ID_MARK
import com.m3u.data.parser.impl.DefaultPlaylistParser.Companion.M3U_TVG_NAME_MARK
import net.bjoernpetersen.m3u.M3uParser
import net.bjoernpetersen.m3u.model.M3uEntry
import java.io.InputStream
import javax.inject.Inject

class ExperimentalPlaylistParser @Inject constructor() : PlaylistParser {

    override suspend fun execute(input: InputStream): List<M3UData> {
        val list = input.use {
            M3uParser.parse(it.reader())
        }
        return list.map { it.toM3UData() }
    }

    private fun M3uEntry.toM3UData(): M3UData {
        return M3UData(
            duration = this.duration?.toMillis()?.toDouble() ?: -1.0,
            url = this.location.url.toString(),
            cover = this.metadata.logo.orEmpty(),
            title = this.title.orEmpty(),
            group = this.metadata[M3U_GROUP_TITLE_MARK].orEmpty(),
            id = this.metadata[M3U_TVG_ID_MARK].orEmpty(),
            name = this.metadata[M3U_TVG_NAME_MARK].orEmpty()
        )
    }
}