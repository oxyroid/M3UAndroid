package com.m3u.data.database.model

import androidx.compose.runtime.Immutable
import com.m3u.data.parser.xtream.XtreamEpisodeInfo
import com.m3u.extension.api.subscription.PlaybackReference

@Immutable
data class SeriesEpisode(
    val id: String,
    val title: String,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val artworkUrl: String? = null,
    val source: SeriesEpisodeSource,
) {
    val sequenceLabel: String?
        get() = when {
            seasonNumber != null && episodeNumber != null ->
                "S${seasonNumber.toString().padStart(2, '0')}" +
                    "E${episodeNumber.toString().padStart(2, '0')}"
            episodeNumber != null -> episodeNumber.toString()
            source is SeriesEpisodeSource.Xtream -> source.episode.episodeNum
            else -> null
        }
}

@Immutable
sealed interface SeriesEpisodeSource {
    @Immutable
    data class Xtream(
        val episode: XtreamEpisodeInfo,
    ) : SeriesEpisodeSource

    @Immutable
    data class Provider(
        val reference: PlaybackReference,
    ) : SeriesEpisodeSource
}

fun Channel.copySeriesEpisode(episode: SeriesEpisode): Channel = when (
    val source = episode.source
) {
    is SeriesEpisodeSource.Xtream -> copyXtreamEpisode(source.episode).copy(
        mediaKind = MediaKinds.EPISODE,
        playable = true,
        browsable = false,
    )

    is SeriesEpisodeSource.Provider -> copy(
        title = episode.title,
        cover = episode.artworkUrl ?: cover,
        mediaKind = MediaKinds.EPISODE,
        playable = true,
        browsable = false,
        relationId = "episode:${episode.id}",
    )
}
