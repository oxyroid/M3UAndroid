package com.m3u.data.service

import android.graphics.Rect
import android.net.Uri
import android.view.Surface
import androidx.compose.runtime.Immutable
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.Playlist
import com.m3u.data.parser.xtream.XtreamChannelInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import java.io.FileDescriptor

interface PlayerManager {
    val player: StateFlow<Player?>
    val size: StateFlow<Rect>

    val channel: StateFlow<Channel?>
    val playlist: StateFlow<Playlist?>

    val playbackState: StateFlow<@Player.State Int>
    val playbackException: StateFlow<PlaybackException?>
    val isPlaying: StateFlow<Boolean>

    val tracksGroups: StateFlow<List<Tracks.Group>>
    val cacheSpace: Flow<Long>

    fun chooseTrack(group: TrackGroup, index: Int)
    fun clearTrack(type: @C.TrackType Int)
    suspend fun play(
        command: MediaCommand,
        applyContinueWatching: Boolean = true
    )
    suspend fun replay()
    fun release()
    fun clearCache()
    fun pauseOrContinue(value: Boolean)
    fun updateSpeed(race: Float)

    suspend fun recordVideo(uri: Uri)
}

@Immutable
sealed class MediaCommand(open val channelId: Int) {
    data class Common(override val channelId: Int) : MediaCommand(channelId)
    data class XtreamEpisode(
        override val channelId: Int,
        val episode: XtreamChannelInfo.Episode
    ) : MediaCommand(channelId)
}

val PlayerManager.tracks: Flow<Map<@C.TrackType Int, List<Format>>>
    get() = tracksGroups.mapLatest { all ->
        // Group all tracks by their type
        all.groupBy { it.type }
            .mapValues { (_, innerGroups) ->
                // For each group, flatten the list of tracks and filter out unsupported tracks
                innerGroups.flatMap { trackGroup ->
                    List(trackGroup.length) { index ->
                        trackGroup.takeIf { it.isTrackSupported(index) }?.getTrackFormat(index)
                    }.filterNotNull()
                }
            }
    }.flowOn(Dispatchers.IO)

val PlayerManager.currentTracks: Flow<Map<@C.TrackType Int, Format?>>
    get() = tracksGroups.mapLatest { currentTracksGroups ->
        currentTracksGroups
            .groupBy { it.type }
            .mapValues { (_, tracksGroups) ->
                tracksGroups
                    .asSequence()
                    .mapNotNull { trackGroup ->
                        (0 until trackGroup.length)
                            .firstOrNull {
                                trackGroup.isTrackSupported(it) &&
                                        trackGroup.isTrackSelected(it)
                            }
                            ?.let { trackGroup.getTrackFormat(it) }
                    }
                    .firstOrNull()
            }
    }.flowOn(Dispatchers.IO)