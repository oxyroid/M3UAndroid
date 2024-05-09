package com.m3u.features.playlist.configuration

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import com.m3u.core.architecture.dispatcher.Dispatcher
import com.m3u.core.architecture.dispatcher.M3uDispatchers.IO
import com.m3u.data.database.model.Playlist
import com.m3u.data.repository.playlist.PlaylistRepository
import com.m3u.data.repository.programme.ProgrammeRepository
import com.m3u.data.worker.SubscriptionWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

internal typealias EpgManifest = Map<Playlist, Boolean>

@HiltViewModel
class PlaylistConfigurationViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val programmeRepository: ProgrammeRepository,
    private val workManager: WorkManager,
    @Dispatcher(IO) ioDispatcher: CoroutineDispatcher,
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
    internal val subscribingOrRefreshing: StateFlow<Boolean> = workManager
        .getWorkInfosFlow(
            WorkQuery.fromStates(
                WorkInfo.State.RUNNING,
                WorkInfo.State.ENQUEUED,
            )
        )
        .combine(playlistUrl) { infos, playlistUrl ->
            infos.any { info ->
                var isCorrectedWorker = false
                var isCurrentPlaylist = false
                info.tags.forEach { tag ->
                    if (SubscriptionWorker.TAG == tag) isCorrectedWorker = true
                    if (playlistUrl == tag) isCurrentPlaylist = true
                    if (isCorrectedWorker && isCurrentPlaylist) return@any true
                }
                false
            }
        }
        .flowOn(ioDispatcher)
        .stateIn(
            scope = viewModelScope,
            initialValue = false,
            started = SharingStarted.WhileSubscribed(5000)
        )

    internal val expired: StateFlow<LocalDateTime?> = playlistUrl
        .flatMapLatest { playlistUrl ->
            programmeRepository.observeProgrammeRange(playlistUrl)
        }
        .map { range ->
            if (range.start == 0L || range.end == 0L) null
            else Instant
                .fromEpochMilliseconds(range.end)
                .toLocalDateTime(TimeZone.currentSystemDefault())
        }
        .stateIn(
            scope = viewModelScope,
            initialValue = null,
            started = SharingStarted.WhileSubscribed(5_000L)
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

    internal fun onSyncProgrammes() {
        val playlistUrl = playlistUrl.value
        SubscriptionWorker.epg(workManager, playlistUrl, true)
    }
}