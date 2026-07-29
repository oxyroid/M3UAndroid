package com.m3u.tv

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.Playlist
import com.m3u.data.repository.channel.ChannelRepository
import com.m3u.data.repository.playlist.PlaylistRepository
import com.m3u.data.repository.tv.TvRepository
import com.m3u.data.service.DPadReactionService
import com.m3u.data.service.MediaCommand
import com.m3u.data.service.PlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
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
    val loadingChannels: Boolean = false,
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
    private var observedFavorites: List<Channel> = emptyList()
    private var observedRecent: Channel? = null
    @Volatile
    private var sortLocale: Locale = Locale.getDefault()

    fun updateLocale(localeTag: String) {
        val requestedLocaleTag = localeTag.trim().takeIf(String::isNotEmpty)
        val locale = requestedLocaleTag
            ?.let(Locale::forLanguageTag)
            ?: Locale.getDefault()
        if (locale != sortLocale) {
            sortLocale = locale
            _state.update { state ->
                state.copy(
                    playlists = state.playlists.sortedWith(
                        localeAwareComparator(
                            primarySelector = Playlist::title,
                            locale = locale,
                        )
                    ),
                    channels = state.channels.sortedWith(
                        localeAwareComparator(
                            primarySelector = Channel::category,
                            secondarySelector = Channel::title,
                            locale = locale,
                        )
                    ),
                )
            }
        }
    }

    init {
        observePlaylists()
        observeFavorites()
        observeRecent()
    }

    fun selectPlaylist(playlist: Playlist) {
        val availablePlaylist = _state.value.playlists
            .firstOrNull { candidate ->
                candidate.url == playlist.url && tvSupportsPlaylistSource(candidate.source)
            }
            ?: return
        if (_state.value.selectedPlaylist?.url == availablePlaylist.url) return
        _state.update {
            it.copy(
                selectedPlaylist = availablePlaylist,
                channels = emptyList(),
            )
        }
        loadChannels(availablePlaylist)
    }

    fun refreshSelectedPlaylist() {
        val playlist = state.value.selectedPlaylist
            ?.takeIf { tvSupportsPlaylistSource(it.source) }
            ?: return
        viewModelScope.launch {
            playlistRepository.refresh(playlist.url)
            loadChannels(playlist)
        }
    }

    fun play(channel: Channel): Boolean {
        if (!_state.value.supports(channel)) return false
        viewModelScope.launch {
            playerManager.play(MediaCommand.Common(channel.id))
            channelRepository.reportPlayed(channel.id)
        }
        return true
    }

    fun playRecent(): Boolean = state.value.recent?.let(::play) ?: false

    fun toggleFavorite(channel: Channel) {
        if (!_state.value.supports(channel)) return
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
                    val supportedCounts = counts.filterKeys { playlist ->
                        tvSupportsPlaylistSource(playlist.source)
                    }
                    val playlists = supportedCounts.keys
                        .sortedWith(
                            localeAwareComparator(
                                primarySelector = Playlist::title,
                                locale = sortLocale,
                            )
                        )
                    val previous = state.selectedPlaylist
                    val selected = previous
                        ?.let { active -> playlists.firstOrNull { it.url == active.url } }
                        ?: playlists.firstOrNull()
                    val previousCount = previous?.let { playlist -> state.counts.countFor(playlist.url) }
                    val selectedCount = selected?.let { playlist ->
                        supportedCounts.countFor(playlist.url)
                    }
                    val supportedPlaylistUrls = playlists
                        .mapTo(mutableSetOf()) { playlist -> playlist.url }

                    _state.update {
                        it.copy(
                            playlists = playlists,
                            counts = supportedCounts,
                            selectedPlaylist = selected,
                            channels = it.channels.filter { channel ->
                                channel.playlistUrl == selected?.url &&
                                    channel.playlistUrl in supportedPlaylistUrls
                            },
                            favorites = observedFavorites.filter { channel ->
                                channel.playlistUrl in supportedPlaylistUrls
                            },
                            recent = observedRecent?.takeIf { channel ->
                                channel.playlistUrl in supportedPlaylistUrls
                            },
                            loadingChannels = selected != null && it.loadingChannels,
                        )
                    }

                    if (selected != null && (selected.url != previous?.url || selectedCount != previousCount)) {
                        loadChannels(selected)
                    } else if (selected == null) {
                        loadChannelsJob?.cancel()
                        loadChannelsJob = null
                    }
                }
        }
    }

    private fun observeFavorites() {
        viewModelScope.launch {
            channelRepository.observeAllFavorite().collect { favorites ->
                observedFavorites = favorites
                _state.update { state ->
                    state.copy(
                        favorites = favorites.filter { channel ->
                            state.supports(channel)
                        },
                    )
                }
            }
        }
    }

    private fun observeRecent() {
        viewModelScope.launch {
            channelRepository.observePlayedRecently().collect { recent ->
                observedRecent = recent
                _state.update { state ->
                    state.copy(
                        recent = recent?.takeIf { channel ->
                            state.supports(channel)
                        },
                    )
                }
            }
        }
    }

    private fun loadChannels(playlist: Playlist) {
        if (!tvSupportsPlaylistSource(playlist.source)) return
        if (_state.value.playlists.none { it.url == playlist.url }) return
        val url = playlist.url
        loadChannelsJob?.cancel()
        loadChannelsJob = viewModelScope.launch(Dispatchers.IO) {
            _state.update { state ->
                if (state.selectedPlaylist?.url == url) {
                    state.copy(loadingChannels = true)
                } else {
                    state
                }
            }
            val channels = channelRepository
                .getByPlaylistUrl(url)
                .filter { channel ->
                    channel.playlistUrl == url && !channel.hidden
                }
                .sortedWith(
                    localeAwareComparator(
                        primarySelector = Channel::category,
                        secondarySelector = Channel::title,
                        locale = sortLocale,
                    )
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

    private fun TvUiState.supports(channel: Channel): Boolean =
        playlists.any { playlist ->
            playlist.url == channel.playlistUrl &&
                tvSupportsPlaylistSource(playlist.source)
        }

    private fun Map<Playlist, Int>.countFor(url: String): Int? =
        entries.firstOrNull { it.key.url == url }?.value
}
