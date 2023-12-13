package com.m3u.features.main

import androidx.lifecycle.viewModelScope
import com.m3u.core.architecture.viewmodel.BaseViewModel
import com.m3u.data.repository.PlaylistRepository
import com.m3u.data.repository.StreamRepository
import com.m3u.features.main.model.PlaylistDetail
import com.m3u.features.main.model.PlaylistDetailHolder
import com.m3u.features.main.model.toDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    streamRepository: StreamRepository,
) : BaseViewModel<MainState, MainEvent, MainMessage>(
    emptyState = MainState()
) {
    private val counts: StateFlow<Map<String, Int>> = streamRepository
        .observeAll()
        .map { streams ->
            streams
                .groupBy { it.playlistUrl }
                .mapValues { it.value.size }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyMap()
        )

    internal val playlists: StateFlow<PlaylistDetailHolder> = playlistRepository
        .observeAll()
        .distinctUntilChanged()
        .combine(counts) { fs, cs ->
            withContext(Dispatchers.Default) {
                fs.map { f ->
                    f.toDetail(cs[f.url] ?: PlaylistDetail.DEFAULT_COUNT)
                }
            }
        }
        .map { PlaylistDetailHolder(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = PlaylistDetailHolder()
        )

    override fun onEvent(event: MainEvent) {
        when (event) {
            is MainEvent.Unsubscribe -> unsubscribe(event.url)
            is MainEvent.Rename -> rename(event.playlistUrl, event.target)
        }
    }

    private fun unsubscribe(url: String) {
        viewModelScope.launch {
            val playlist = playlistRepository.unsubscribe(url)
            if (playlist == null) {
                onMessage(MainMessage.ErrorCannotUnsubscribe)
            }
        }
    }

    private fun rename(playlistUrl: String, target: String) {
        viewModelScope.launch {
            playlistRepository.rename(playlistUrl, target)
        }
    }
}