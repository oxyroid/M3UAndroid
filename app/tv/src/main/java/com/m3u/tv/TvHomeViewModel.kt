package com.m3u.tv

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.DataSource
import com.m3u.data.database.model.Playlist
import com.m3u.data.repository.channel.ChannelRepository
import com.m3u.data.repository.playlist.PlaylistRepository
import com.m3u.data.repository.tv.TvRepository
import com.m3u.data.service.DPadReactionService
import com.m3u.data.service.MediaCommand
import com.m3u.data.service.PlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class TvUiState(
    val playlists: List<Playlist> = emptyList(),
    val counts: Map<Playlist, Int> = emptyMap(),
    val selectedPlaylist: Playlist? = null,
    val channels: List<Channel> = emptyList(),
    val favorites: List<Channel> = emptyList(),
    val recent: Channel? = null,
    val loadingChannels: Boolean = false
) {
    val channelCount: Int get() = counts.values.sum()
    val heroChannel: Channel? get() = recent ?: channels.firstOrNull()
}

@HiltViewModel
class TvHomeViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val channelRepository: ChannelRepository,
    private val playerManager: PlayerManager,
    tvRepository: TvRepository,
    dPadReactionService: DPadReactionService
) : ViewModel() {
    private val _state = MutableStateFlow(TvUiState())
    val state: StateFlow<TvUiState> = _state.asStateFlow()

    val player: StateFlow<Player?> = playerManager.player
    val currentChannel: StateFlow<Channel?> = playerManager.channel
    val isPlaying: StateFlow<Boolean> = playerManager.isPlaying
    val playbackState: StateFlow<Int> = playerManager.playbackState
    val remoteControlCode: StateFlow<Int?> = tvRepository.broadcastCodeOnTv
    val remoteDirections = dPadReactionService.incoming
    private var loadChannelsJob: Job? = null

    init {
        observePlaylists()
        observeFavorites()
        observeRecent()
    }

    fun selectPlaylist(playlist: Playlist) {
        if (_state.value.selectedPlaylist?.url == playlist.url) return
        _state.update { it.copy(selectedPlaylist = playlist) }
        loadChannels(playlist.url)
    }

    fun refreshSelectedPlaylist() {
        val playlist = state.value.selectedPlaylist ?: return
        viewModelScope.launch {
            playlistRepository.refresh(playlist.url)
            loadChannels(playlist.url)
        }
    }

    fun play(channel: Channel) {
        viewModelScope.launch {
            playerManager.play(MediaCommand.Common(channel.id))
            channelRepository.reportPlayed(channel.id)
        }
    }

    fun playRecent() {
        state.value.recent?.let(::play)
    }

    fun toggleFavorite(channel: Channel) {
        viewModelScope.launch {
            channelRepository.favouriteOrUnfavourite(channel.id)
        }
    }

    fun pauseOrContinue(continuePlayback: Boolean) {
        playerManager.pauseOrContinue(continuePlayback)
    }

    fun releasePlayer() {
        playerManager.release()
    }

    private fun observePlaylists() {
        viewModelScope.launch {
            playlistRepository
                .observeAllCounts()
                .flowOn(Dispatchers.Default)
                .collect { counts ->
                    val state = _state.value
                    val playlists = counts.keys
                        .filterNot { it.source == DataSource.EPG }
                        .sortedBy { it.title.lowercase() }
                    val previous = state.selectedPlaylist
                    val selected = previous
                        ?.let { active -> playlists.firstOrNull { it.url == active.url } }
                        ?: playlists.firstOrNull()
                    val previousCount = previous?.let { playlist -> state.counts.countFor(playlist.url) }
                    val selectedCount = selected?.let { playlist -> counts.countFor(playlist.url) }

                    _state.update {
                        it.copy(
                            playlists = playlists,
                            counts = counts,
                            selectedPlaylist = selected
                        )
                    }

                    if (selected != null && (selected.url != previous?.url || selectedCount != previousCount)) {
                        loadChannels(selected.url)
                    }
                }
        }
    }

    private fun observeFavorites() {
        viewModelScope.launch {
            channelRepository.observeAllFavorite().collect { favorites ->
                _state.update { it.copy(favorites = favorites) }
            }
        }
    }

    private fun observeRecent() {
        viewModelScope.launch {
            channelRepository.observePlayedRecently().collect { recent ->
                _state.update { it.copy(recent = recent) }
            }
        }
    }

    private fun loadChannels(url: String) {
        loadChannelsJob?.cancel()
        loadChannelsJob = viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(loadingChannels = true) }
            val channels = channelRepository
                .getByPlaylistUrl(url)
                .filterNot { it.hidden }
                .sortedWith(
                    compareBy<Channel> { it.category.lowercase() }
                        .thenBy { it.title.lowercase() }
                )
            _state.update { state ->
                if (state.selectedPlaylist?.url == url) {
                    state.copy(
                        channels = channels,
                        loadingChannels = false
                    )
                } else {
                    state
                }
            }
        }
    }

    private fun Map<Playlist, Int>.countFor(url: String): Int? =
        entries.firstOrNull { it.key.url == url }?.value
}