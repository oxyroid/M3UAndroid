package com.m3u.data.service

import android.graphics.Rect
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import com.m3u.data.api.xtream.XtreamStreamInfo
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.Stream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

interface PlayerManagerV2 {
    val player: StateFlow<Player?>
    val size: StateFlow<Rect>

    val stream: StateFlow<Stream?>
    val playlist: StateFlow<Playlist?>

    val playbackState: StateFlow<@Player.State Int>
    val playbackException: StateFlow<PlaybackException?>

    val tracksGroups: StateFlow<List<Tracks.Group>>
    fun chooseTrack(group: TrackGroup, index: Int)
    fun clearTrack(type: @C.TrackType Int)
    suspend fun play(input: Input)
    suspend fun replay()
    fun release()

    val inputChannel: StateFlow<Input?>

    sealed class Input(open val streamId: Int) {
        data class Live(override val streamId: Int) : Input(streamId)
        data class XtreamEpisode(
            override val streamId: Int,
            val episode: XtreamStreamInfo.Episode
        ) : Input(streamId)
    }
    fun download()
}

val PlayerManagerV2.trackFormats: Flow<Map<@C.TrackType Int, List<Format>>>
    get() = tracksGroups.map { all ->
        all.groupBy { it.type }
            .mapValues { (_, innerGroups) ->
                innerGroups
                    .map { group -> List(group.length) { group.getTrackFormat(it) } }
                    .flatten()
            }
    }

val PlayerManagerV2.selectedFormats: Flow<Map<@C.TrackType Int, Format?>>
    get() = tracksGroups.map { all ->
        all.groupBy { it.type }
            .mapValues { (_, groups) ->
                var format: Format? = null
                outer@ for (group in groups) {
                    var selectedIndex = -1
                    inner@ for (i in 0 until group.length) {
                        if (group.isTrackSelected(i)) {
                            selectedIndex = i
                            break@inner
                        }
                    }
                    if (selectedIndex != -1) {
                        format = group.getTrackFormat(selectedIndex)
                        break@outer
                    }
                }
                format
            }
    }