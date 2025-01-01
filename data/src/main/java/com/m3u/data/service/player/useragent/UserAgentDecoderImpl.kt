package com.m3u.data.service.player.useragent

import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.Playlist
import com.m3u.data.service.internal.KodiAdaptions
import javax.inject.Inject

internal class UserAgentDecoderImpl @Inject constructor() : UserAgentDecoder {
    override fun decodeUserAgent(channel: Channel, playlist: Playlist?): String? {
        val channelUrl = channel.url
        val kodiUrlOptions = channelUrl.readKodiUrlOptions()
        val userAgent = kodiUrlOptions[KodiAdaptions.HTTP_OPTION_UA] ?: playlist?.userAgent
        return userAgent
    }

    /**
     * Get the kodi url options like this:
     * http://host[:port]/directory/file?a=b&c=d|option1=value1&option2=value2
     * Will get:
     * {option1=value1, option2=value2}
     *
     * https://kodi.wiki/view/HTTP
     */
    private fun String.readKodiUrlOptions(): Map<String, String?> {
        val options = this.drop(this.indexOf("|") + 1).split("&")
        return options
            .filter { it.isNotBlank() }
            .associate {
                val pair = it.split("=")
                val key = pair.getOrNull(0).orEmpty()
                val value = pair.getOrNull(1)
                key to value
            }
    }
}