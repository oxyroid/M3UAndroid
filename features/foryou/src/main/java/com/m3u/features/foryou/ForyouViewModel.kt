package com.m3u.features.foryou

import androidx.lifecycle.viewModelScope
import com.m3u.core.architecture.viewmodel.BaseViewModel
import com.m3u.data.repository.PlaylistRepository
import com.m3u.data.repository.StreamRepository
import com.m3u.features.foryou.model.PlaylistDetail
import com.m3u.features.foryou.model.PlaylistDetailHolder
import com.m3u.features.foryou.model.Unseens
import com.m3u.features.foryou.model.toDetail
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
import kotlin.time.Duration.Companion.days

@HiltViewModel
class ForyouViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    streamRepository: StreamRepository,
) : BaseViewModel<ForyouState, ForyouEvent, ForyouMessage>(
    emptyState = ForyouState()
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

    internal val unseens: StateFlow<Unseens> = streamRepository
        .observeAllUnseenFavourites(5.days)
        .map { Unseens(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = Unseens()
        )

    override fun onEvent(event: ForyouEvent) {
        when (event) {
            is ForyouEvent.Unsubscribe -> unsubscribe(event.url)
            is ForyouEvent.Rename -> rename(event.playlistUrl, event.target)
        }
    }

    private fun unsubscribe(url: String) {
        viewModelScope.launch {
            val playlist = playlistRepository.unsubscribe(url)
            if (playlist == null) {
                onMessage(ForyouMessage.ErrorCannotUnsubscribe)
            }
        }
    }

    private fun rename(playlistUrl: String, target: String) {
        viewModelScope.launch {
            playlistRepository.rename(playlistUrl, target)
        }
    }
}