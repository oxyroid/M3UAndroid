package com.m3u.features.playlist.configuration

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m3u.data.database.model.Playlist
import com.m3u.data.repository.playlist.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

internal typealias EpgManifest = Map<Playlist, Boolean>

@HiltViewModel
class PlaylistConfigurationViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val playlistUrl: StateFlow<String> = savedStateHandle
        .getStateFlow(PlaylistConfigurationNavigation.TYPE_PLAYLIST_URL, "")
    internal val playlist: StateFlow<Playlist?> = playlistUrl.flatMapLatest {
        playlistRepository.observe(it)
    }
        .stateIn(
            scope = viewModelScope,
            initialValue = null,
            started = SharingStarted.WhileSubscribed(5_000L)
        )

    internal val manifest: StateFlow<EpgManifest> = combine(
        playlistRepository.observeAllEpgs(),
        playlist
    ) { epgs, playlist ->
        val epgUrls = playlist?.epgUrls ?: return@combine emptyMap()
        buildMap {
            epgs.forEach { epg ->
                put(epg, epg.url in epgUrls)
            }
        }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = emptyMap()
        )

    internal fun onUpdatePlaylistTitle(title: String) {
        val playlistUrl = playlistUrl.value
        viewModelScope.launch {
            playlistRepository.onUpdatePlaylistTitle(playlistUrl, title)
        }
    }

    internal fun onUpdatePlaylistUserAgent(userAgent: String?) {
        val playlistUrl = playlistUrl.value
        viewModelScope.launch {
            playlistRepository.onUpdatePlaylistUserAgent(playlistUrl, userAgent)
        }
    }

    internal fun onUpdateEpgPlaylist(usecase: PlaylistRepository.UpdateEpgPlaylistUseCase) {
        viewModelScope.launch {
            playlistRepository.onUpdateEpgPlaylist(usecase)
        }
    }
}