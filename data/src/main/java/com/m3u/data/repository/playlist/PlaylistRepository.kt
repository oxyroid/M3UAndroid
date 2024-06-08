package com.m3u.data.repository.playlist

import android.net.Uri
import androidx.compose.runtime.Immutable
import com.m3u.data.database.model.DataSource
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.PlaylistWithCount
import com.m3u.data.database.model.PlaylistWithChannels
import com.m3u.data.database.model.Channel
import com.m3u.data.parser.xtream.XtreamChannelInfo
import kotlinx.coroutines.flow.Flow

interface PlaylistRepository {
    fun observeAll(): Flow<List<Playlist>>
    fun observeAllEpgs(): Flow<List<Playlist>>
    fun observePlaylistUrls(): Flow<List<String>>
    suspend fun get(url: String): Playlist?
    suspend fun getAll(): List<Playlist>
    suspend fun getAllAutoRefresh(): List<Playlist>
    suspend fun getBySource(source: DataSource): List<Playlist>
    suspend fun getCategoriesByPlaylistUrlIgnoreHidden(url: String, query: String): List<String>
    fun observeCategoriesByPlaylistUrlIgnoreHidden(url: String, query: String): Flow<List<String>>
    fun observe(url: String): Flow<Playlist?>
    fun observePlaylistWithChannels(url: String): Flow<PlaylistWithChannels?>
    suspend fun getPlaylistWithChannels(url: String): PlaylistWithChannels?

    suspend fun m3uOrThrow(
        title: String,
        url: String,
        callback: (count: Int) -> Unit = {}
    )

    suspend fun xtreamOrThrow(
        title: String,
        basicUrl: String,
        username: String,
        password: String,
        type: String?,
        callback: (count: Int) -> Unit = {}
    )

    suspend fun insertEpgAsPlaylist(title: String, epg: String)

    suspend fun refresh(url: String)

    suspend fun unsubscribe(url: String): Playlist?

    suspend fun onUpdatePlaylistTitle(url: String, title: String)

    suspend fun backupOrThrow(uri: Uri)

    suspend fun restoreOrThrow(uri: Uri)

    suspend fun pinOrUnpinCategory(url: String, category: String)

    suspend fun hideOrUnhideCategory(url: String, category: String)

    suspend fun onUpdatePlaylistUserAgent(url: String, userAgent: String?)

    fun observeAllCounts(): Flow<List<PlaylistWithCount>>

    suspend fun readEpisodesOrThrow(series: Channel): List<XtreamChannelInfo.Episode>

    suspend fun deleteEpgPlaylistAndProgrammes(epgUrl: String)

    suspend fun onUpdateEpgPlaylist(useCase: UpdateEpgPlaylistUseCase)
    suspend fun onUpdatePlaylistAutoRefreshProgrammes(playlistUrl: String)

    @Immutable
    data class UpdateEpgPlaylistUseCase(
        val playlistUrl: String,
        val epgUrl: String,
        val action: Boolean
    )
}
