package com.m3u.business.search

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.DataSource
import com.m3u.data.parser.xtream.XtreamInput
import com.m3u.data.repository.channel.ChannelRepository
import com.m3u.data.repository.playlist.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class GlobalSearchState(
    val categories: List<String> = emptyList(),
    val channels: List<Channel> = emptyList(),
    val liveStreams: List<Channel> = emptyList(),
    val vod: List<Channel> = emptyList(),
    val playlistTitles: Map<String, String> = emptyMap()
) {
    val isEmpty: Boolean get() = categories.isEmpty() && channels.isEmpty() && liveStreams.isEmpty() && vod.isEmpty()
}

@OptIn(FlowPreview::class)
@HiltViewModel
class GlobalSearchViewModel @Inject constructor(
    private val channelRepository: ChannelRepository,
    private val playlistRepository: PlaylistRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _state = MutableStateFlow(GlobalSearchState())
    val state: StateFlow<GlobalSearchState> = _state.asStateFlow()

    val isSearching: StateFlow<Boolean> = _query
        .map { it.length >= 3 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        viewModelScope.launch {
            _query
                .debounce(300)
                .filter { it.length >= 3 }
                .collect { query -> performSearch(query) }
        }
    }

    fun onQueryChange(value: String) {
        _query.value = value
        if (value.length < 3) {
            _state.value = GlobalSearchState()
        }
    }

    private suspend fun performSearch(query: String) {
        val playlists = playlistRepository.getAll()
        val playlistTitles = playlists.associate { it.url to it.title }

        val livePlaylistUrls = playlists
            .filter { it.source == DataSource.Xtream }
            .filter {
                runCatching {
                    XtreamInput.decodeFromPlaylistUrl(it.url).type == DataSource.Xtream.TYPE_LIVE
                }.getOrDefault(false)
            }
            .map { it.url }

        val vodPlaylistUrls = playlists
            .filter { it.source == DataSource.Xtream }
            .filter {
                runCatching {
                    XtreamInput.decodeFromPlaylistUrl(it.url).type == DataSource.Xtream.TYPE_VOD
                }.getOrDefault(false)
            }
            .map { it.url }

        val categories = channelRepository.searchCategories(query)
        val channels = channelRepository.searchByPrefix(query)
        val liveStreams = channelRepository.searchByPlaylistUrls(query, livePlaylistUrls)
        val vod = channelRepository.searchByPlaylistUrls(query, vodPlaylistUrls)

        _state.value = GlobalSearchState(
            categories = categories,
            channels = channels,
            liveStreams = liveStreams,
            vod = vod,
            playlistTitles = playlistTitles
        )
    }

    suspend fun findPlaylistUrlForCategory(category: String): String? {
        return channelRepository.findPlaylistUrlForCategory(category)
    }
}
