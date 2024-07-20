package com.m3u.feature.playlist.configuration

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import com.m3u.core.architecture.dispatcher.Dispatcher
import com.m3u.core.architecture.dispatcher.M3uDispatchers.IO
import com.m3u.core.architecture.logger.Logger
import com.m3u.core.architecture.logger.Profiles
import com.m3u.core.architecture.logger.install
import com.m3u.core.architecture.logger.post
import com.m3u.core.wrapper.Resource
import com.m3u.core.wrapper.asResource
import com.m3u.data.database.model.DataSource
import com.m3u.data.database.model.Playlist
import com.m3u.data.parser.xtream.XtreamInfo
import com.m3u.data.parser.xtream.XtreamInput
import com.m3u.data.parser.xtream.XtreamParser
import com.m3u.data.repository.playlist.PlaylistRepository
import com.m3u.data.repository.programme.ProgrammeRepository
import com.m3u.data.worker.SubscriptionWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
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
    private val xtreamParser: XtreamParser,
    private val workManager: WorkManager,
    @Dispatcher(IO) ioDispatcher: CoroutineDispatcher,
    savedStateHandle: SavedStateHandle,
    delegate: Logger
) : ViewModel() {
    private val logger = delegate.install(Profiles.VIEWMODEL_PLAYLIST_CONFIGURATION)
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

    internal val xtreamUserInfo: StateFlow<Resource<XtreamInfo.UserInfo>> =
        playlist.map { playlist ->
            playlist ?: return@map null
            if (playlist.source != DataSource.Xtream) return@map null
            val xtreamInput = XtreamInput
                .decodeFromPlaylistUrlOrNull(playlist.url)
                ?: return@map null
            xtreamParser
                .getInfo(xtreamInput)
                .userInfo
        }
            .filterNotNull()
            .asResource()
            .stateIn(
                scope = viewModelScope,
                initialValue = Resource.Loading,
                started = SharingStarted.Lazily
            )

    internal val manifest: StateFlow<EpgManifest> = combine(
        playlistRepository.observeAllEpgs().onEach { logger.post { it.size } },
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
    internal val subscribingOrRefreshingWorkInfo: StateFlow<WorkInfo?> = workManager
        .getWorkInfosFlow(
            WorkQuery.fromStates(
                WorkInfo.State.RUNNING,
                WorkInfo.State.ENQUEUED,
            )
        )
        .combine(playlistUrl) { infos, playlistUrl ->
            infos.find { info ->
                info.tags.containsAll(
                    listOf(SubscriptionWorker.TAG, DataSource.EPG.value, playlistUrl)
                )
            }
        }
        .flowOn(ioDispatcher)
        .stateIn(
            scope = viewModelScope,
            initialValue = null,
            started = SharingStarted.WhileSubscribed(5_000L)
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

    internal fun onUpdatePlaylistAutoRefreshProgrammes() {
        val playlistUrl = playlistUrl.value
        viewModelScope.launch {
            playlistRepository.onUpdatePlaylistAutoRefreshProgrammes(playlistUrl)
        }
    }

    internal fun onSyncProgrammes() {
        val playlistUrl = playlistUrl.value
        SubscriptionWorker.epg(workManager, playlistUrl, true)
    }

    internal fun onCancelSyncProgrammes() {
        val workInfo = subscribingOrRefreshingWorkInfo.value
        workInfo?.id?.let { workManager.cancelWorkById(it) }
    }
}