package com.m3u.data.service.player.mediacommand

import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.copyXtreamEpisode
import com.m3u.data.database.model.copyXtreamSeries
import com.m3u.data.repository.channel.ChannelRepository
import com.m3u.data.repository.playlist.PlaylistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

internal class MediaCommandDecoderImpl @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val channelRepository: ChannelRepository
) : MediaCommandDecoder {
    override suspend fun decodePlaylist(mediaCommand: MediaCommand?): Playlist? {
        val channel = decodeChannel(mediaCommand) ?: return null
        val playlistUrl = channel.playlistUrl
        val playlist = playlistRepository.get(playlistUrl)?: return null
        return when (mediaCommand) {
            is MediaCommand.Common -> playlist
            is MediaCommand.XtreamEpisode -> playlist.copyXtreamSeries(channel)
            null -> null
        }
    }
    override suspend fun decodeChannel(mediaCommand: MediaCommand?): Channel? =
        when (mediaCommand) {
            is MediaCommand.Common -> channelRepository.get(mediaCommand.channelId)
            is MediaCommand.XtreamEpisode -> channelRepository
                .get(mediaCommand.channelId)
                ?.copyXtreamEpisode(mediaCommand.episode)
            null -> null
        }

    override fun decodePlaylist(mediaCommand: Flow<MediaCommand?>): Flow<Playlist?> {
        return mediaCommand.flatMapLatest { command ->
            when (command) {
                is MediaCommand.Common -> {
                    val channel = channelRepository.get(command.channelId)
                    channel?.let { playlistRepository.observe(it.playlistUrl) } ?: flow { }
                }

                is MediaCommand.XtreamEpisode -> {
                    val channel = channelRepository.get(command.channelId)
                    channel?.let {
                        playlistRepository
                            .observe(it.playlistUrl)
                            .map { prev -> prev?.copyXtreamSeries(channel) }
                    } ?: flowOf(null)
                }

                null -> flowOf(null)
            }
        }
    }

    override fun decodeChannel(mediaCommand: Flow<MediaCommand?>): Flow<Channel?> = mediaCommand
        .flatMapLatest { command ->
            when (command) {
                is MediaCommand.Common -> channelRepository.observe(command.channelId)
                is MediaCommand.XtreamEpisode -> channelRepository
                    .observe(command.channelId)
                    .map { it?.copyXtreamEpisode(command.episode) }

                else -> flowOf(null)
            }
        }
}