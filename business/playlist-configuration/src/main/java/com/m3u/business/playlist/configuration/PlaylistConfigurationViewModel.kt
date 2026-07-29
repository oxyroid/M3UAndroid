package com.m3u.business.playlist.configuration

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import com.m3u.core.foundation.wrapper.Resource
import com.m3u.core.foundation.wrapper.asResource
import com.m3u.data.database.model.DataSource
import com.m3u.data.database.model.Playlist
import com.m3u.data.parser.xtream.XtreamUserInfo
import com.m3u.data.parser.xtream.XtreamInput
import com.m3u.data.parser.xtream.XtreamParser
import com.m3u.data.repository.playlist.PlaylistRepository
import com.m3u.data.repository.playlist.PlaylistRefreshReason
import com.m3u.data.repository.programme.ProgrammeRepository
import com.m3u.data.repository.provider.DiscoveredSubscriptionProvider
import com.m3u.data.repository.provider.ProviderAccountSummary
import com.m3u.data.repository.provider.SubscriptionProviderRepository
import com.m3u.data.worker.SubscriptionWorker
import com.m3u.data.worker.playlistWorkTag
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import timber.log.Timber
import javax.inject.Inject

typealias EpgManifest = Map<Playlist, Boolean>

@HiltViewModel
class PlaylistConfigurationViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val programmeRepository: ProgrammeRepository,
    private val xtreamParser: XtreamParser,
    private val workManager: WorkManager,
    private val subscriptionProviderRepository: SubscriptionProviderRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val timber = Timber.tag("PlaylistConfigurationViewModel")
    private val playlistReference: StateFlow<String> = savedStateHandle
        .getStateFlow(PlaylistConfigurationNavigation.TYPE_PLAYLIST_URL, "")
    private val activeSubscriptionWorkInfos = workManager.getWorkInfosFlow(
        WorkQuery.fromStates(
            WorkInfo.State.BLOCKED,
            WorkInfo.State.RUNNING,
            WorkInfo.State.ENQUEUED,
        )
    )
    private val playlistRefreshLaunch = MutableStateFlow<RefreshLaunch>(RefreshLaunch.Idle)
    private val programmeRefreshLaunch = MutableStateFlow<RefreshLaunch>(RefreshLaunch.Idle)
    val playlistRemovalState: StateFlow<PlaylistRemovalState> =
        savedStateHandle.getStateFlow(
            PLAYLIST_REMOVAL_STATE_KEY,
            PlaylistRemovalState.IDLE,
        )
    private val _discoveredProviders =
        MutableStateFlow<List<DiscoveredSubscriptionProvider>>(emptyList())
    val discoveredProviders: StateFlow<List<DiscoveredSubscriptionProvider>> =
        _discoveredProviders
    private var providerCatalogLocaleTag: String? = null

    val state: StateFlow<PlaylistConfigurationState> = playlistRepository
        .observeAll()
        .combine(playlistReference) { playlists, routeArgument ->
            resolvePlaylistConfigurationState(
                playlists = playlists,
                playlistReference = routeArgument,
            )
        }
        .onEach { configurationState ->
            val currentPlaylist =
                (configurationState as? PlaylistConfigurationState.Content)?.playlist
                    ?: return@onEach
            val canonicalReference =
                playlistConfigurationReference(currentPlaylist.url)
            if (playlistReference.value != canonicalReference) {
                savedStateHandle[PlaylistConfigurationNavigation.TYPE_PLAYLIST_URL] =
                    canonicalReference
            }
        }
        .stateIn(
            scope = viewModelScope,
            initialValue = PlaylistConfigurationState.Loading,
            started = SharingStarted.Eagerly
        )

    val playlist: StateFlow<Playlist?> = state
        .map { configurationState ->
            (configurationState as? PlaylistConfigurationState.Content)?.playlist
        }
        .stateIn(
            scope = viewModelScope,
            initialValue = null,
            started = SharingStarted.Eagerly,
        )

    val providerAccountSummary: StateFlow<ProviderAccountSummary?> =
        subscriptionProviderRepository
            .observeAccountSummaries()
            .combine(state) { summaries, configurationState ->
                val playlistUrl =
                    (configurationState as? PlaylistConfigurationState.Content)
                        ?.playlist
                        ?.url
                        ?: return@combine null
                summaries.firstOrNull { summary ->
                    summary.playlistUrl == playlistUrl
                }
            }
            .stateIn(
                scope = viewModelScope,
                initialValue = null,
                started = SharingStarted.WhileSubscribed(5_000L),
            )

    val xtreamUserInfo: StateFlow<Resource<XtreamUserInfo>> = playlist
        .flatMapLatest { currentPlaylist ->
            if (currentPlaylist?.source != DataSource.Xtream) {
                return@flatMapLatest flowOf(Resource.Loading)
            }
            val xtreamInput = XtreamInput
                .decodeFromPlaylistUrlOrNull(currentPlaylist.url)
                ?: return@flatMapLatest flowOf(Resource.Failure(null))
            flow {
                emit(xtreamParser.getInfo(xtreamInput).userInfo)
            }.asResource()
        }
        .stateIn(
            scope = viewModelScope,
            initialValue = Resource.Loading,
            started = SharingStarted.Lazily
        )

    val manifest: StateFlow<EpgManifest> = combine(
        playlistRepository.observeAllEpgs(),
        playlist
    ) { epgs, playlist ->
        val epgUrls = playlist?.epgUrls ?: return@combine emptyMap()
        epgs.associateWith { it.url in epgUrls }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = emptyMap()
        )
    val playlistRefreshWorkInfo: StateFlow<WorkInfo?> = activeSubscriptionWorkInfos
        .combine(playlist) { infos, currentPlaylist ->
            infos.findPlaylistWork(
                playlist = currentPlaylist,
                includeProgrammeRefresh = false,
            )
        }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            initialValue = null,
            started = SharingStarted.WhileSubscribed(5_000L)
        )

    val programmeRefreshWorkInfo: StateFlow<WorkInfo?> = activeSubscriptionWorkInfos
        .combine(playlist) { infos, currentPlaylist ->
            infos.findPlaylistWork(
                playlist = currentPlaylist,
                includeProgrammeRefresh = true,
            )
        }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            initialValue = null,
            started = SharingStarted.WhileSubscribed(5_000L)
        )

    val playlistRefreshStatus: StateFlow<PlaylistRefreshStatus> =
        refreshStatus(
            launch = playlistRefreshLaunch,
            activeWorkInfo = playlistRefreshWorkInfo,
        )

    val programmeRefreshStatus: StateFlow<PlaylistRefreshStatus> =
        refreshStatus(
            launch = programmeRefreshLaunch,
            activeWorkInfo = programmeRefreshWorkInfo,
        )

    val expired: StateFlow<LocalDateTime?> = playlist
        .flatMapLatest { currentPlaylist ->
            if (currentPlaylist == null) {
                flowOf(null)
            } else {
                programmeRepository
                    .observeProgrammeRange(currentPlaylist.url)
                    .map { range ->
                        if (range.start == 0L || range.end == 0L) {
                            null
                        } else {
                            Instant
                                .fromEpochMilliseconds(range.end)
                                .toLocalDateTime(TimeZone.currentSystemDefault())
                        }
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            initialValue = null,
            started = SharingStarted.WhileSubscribed(5_000L)
        )

    fun onUpdatePlaylistTitle(title: String) {
        val normalizedTitle = normalizePlaylistTitle(title) ?: return
        val playlistUrl = currentPlaylist()?.url ?: return
        viewModelScope.launch {
            playlistRepository.onUpdatePlaylistTitle(playlistUrl, normalizedTitle)
        }
    }

    fun onUpdatePlaylistUserAgent(userAgent: String?) {
        val playlistUrl = currentPlaylist()?.url ?: return
        val normalizedUserAgent = normalizePlaylistUserAgent(userAgent)
        viewModelScope.launch {
            playlistRepository.onUpdatePlaylistUserAgent(playlistUrl, normalizedUserAgent)
        }
    }

    fun onUpdateEpgPlaylist(usecase: PlaylistRepository.EpgPlaylistUseCase) {
        val playlistUrl = currentPlaylist()?.url ?: return
        val resolvedUseCase = when (usecase) {
            is PlaylistRepository.EpgPlaylistUseCase.Check ->
                usecase.copy(playlistUrl = playlistUrl)

            is PlaylistRepository.EpgPlaylistUseCase.Upgrade ->
                usecase.copy(playlistUrl = playlistUrl)
        }
        viewModelScope.launch {
            playlistRepository.onUpdateEpgPlaylist(resolvedUseCase)
        }
    }

    fun onUpdatePlaylistAutoRefreshProgrammes() {
        val playlistUrl = currentPlaylist()?.url ?: return
        viewModelScope.launch {
            playlistRepository.onUpdatePlaylistAutoRefreshProgrammes(playlistUrl)
        }
    }

    fun onRefreshPlaylist() {
        val playlistUrl = currentPlaylist()?.url ?: return
        if (playlistRefreshStatus.value.isInProgress) return
        playlistRefreshLaunch.value = RefreshLaunch.Enqueuing(playlistUrl)
        viewModelScope.launch {
            try {
                val workId = playlistRepository.refreshWithWorkId(
                    url = playlistUrl,
                    reason = PlaylistRefreshReason.USER,
                )
                playlistRefreshLaunch.updateFor(playlistUrl) {
                    workId?.let { RefreshLaunch.Work(playlistUrl, it) }
                        ?: RefreshLaunch.Failed(playlistUrl)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                timber.w(
                    error,
                    "Unable to enqueue playlist refresh (%s)",
                    error.javaClass.simpleName,
                )
                playlistRefreshLaunch.updateFor(playlistUrl) {
                    RefreshLaunch.Failed(playlistUrl)
                }
            }
        }
    }

    fun onCancelRefreshPlaylist() {
        val workId = (playlistRefreshLaunch.value as? RefreshLaunch.Work)
            ?.takeIf { launch -> launch.playlistUrl == currentPlaylist()?.url }
            ?.workId
            ?: playlistRefreshWorkInfo.value?.id
        workId?.let(workManager::cancelWorkById)
    }

    fun onSyncProgrammes() {
        val playlistUrl = currentPlaylist()?.url ?: return
        if (programmeRefreshStatus.value.isInProgress) return
        programmeRefreshLaunch.value = RefreshLaunch.Enqueuing(playlistUrl)
        viewModelScope.launch {
            try {
                val workId = SubscriptionWorker.epg(workManager, playlistUrl, true)
                programmeRefreshLaunch.updateFor(playlistUrl) {
                    RefreshLaunch.Work(playlistUrl, workId)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                timber.w(
                    error,
                    "Unable to enqueue programme refresh (%s)",
                    error.javaClass.simpleName,
                )
                programmeRefreshLaunch.updateFor(playlistUrl) {
                    RefreshLaunch.Failed(playlistUrl)
                }
            }
        }
    }

    fun onCancelSyncProgrammes() {
        val workId = (programmeRefreshLaunch.value as? RefreshLaunch.Work)
            ?.takeIf { launch -> launch.playlistUrl == currentPlaylist()?.url }
            ?.workId
            ?: programmeRefreshWorkInfo.value?.id
        workId?.let(workManager::cancelWorkById)
    }

    fun onRemovePlaylist() {
        if (
            playlistRemovalState.value == PlaylistRemovalState.REMOVING ||
            playlistRemovalState.value == PlaylistRemovalState.REMOVED
        ) {
            return
        }
        val playlistUrl = currentPlaylist()?.url ?: return
        savedStateHandle[PLAYLIST_REMOVAL_STATE_KEY] =
            PlaylistRemovalState.REMOVING
        viewModelScope.launch {
            try {
                playlistRepository.unsubscribe(playlistUrl)
                savedStateHandle[PLAYLIST_REMOVAL_STATE_KEY] =
                    PlaylistRemovalState.REMOVED
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                savedStateHandle[PLAYLIST_REMOVAL_STATE_KEY] =
                    PlaylistRemovalState.FAILED
            }
        }
    }

    fun clearPlaylistRemovalFailure() {
        if (playlistRemovalState.value == PlaylistRemovalState.FAILED) {
            savedStateHandle[PLAYLIST_REMOVAL_STATE_KEY] =
                PlaylistRemovalState.IDLE
        }
    }

    fun refreshProviderCatalog(localeTag: String) {
        if (
            providerCatalogLocaleTag == localeTag &&
            _discoveredProviders.value.isNotEmpty()
        ) {
            return
        }
        providerCatalogLocaleTag = localeTag
        viewModelScope.launch {
            try {
                val providers =
                    subscriptionProviderRepository.discoverProviders(localeTag)
                if (providerCatalogLocaleTag == localeTag) {
                    _discoveredProviders.value = providers
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // The account/server name remains a readable offline fallback.
            }
        }
    }

    fun acknowledgePlaylistRemoval() {
        if (playlistRemovalState.value == PlaylistRemovalState.REMOVED) {
            savedStateHandle[PLAYLIST_REMOVAL_STATE_KEY] =
                PlaylistRemovalState.IDLE
        }
    }

    fun openPlaylistReference(reference: String) {
        savedStateHandle[PlaylistConfigurationNavigation.TYPE_PLAYLIST_URL] = reference
    }

    private fun currentPlaylist(): Playlist? =
        (state.value as? PlaylistConfigurationState.Content)?.playlist

    private fun refreshStatus(
        launch: StateFlow<RefreshLaunch>,
        activeWorkInfo: StateFlow<WorkInfo?>,
    ): StateFlow<PlaylistRefreshStatus> = combine(
        playlist,
        launch,
    ) { currentPlaylist, currentLaunch ->
        currentPlaylist?.url to currentLaunch
    }
        .flatMapLatest { (playlistUrl, currentLaunch) ->
            val applicableLaunch = currentLaunch.takeIf { launchState ->
                launchState.playlistUrl == playlistUrl
            } ?: RefreshLaunch.Idle
            val trackedWork = (applicableLaunch as? RefreshLaunch.Work)
            val trackedWorkInfo = trackedWork?.let { work ->
                workManager.getWorkInfoByIdFlow(work.workId)
            } ?: flowOf(null)
            combine(
                trackedWorkInfo,
                activeWorkInfo,
            ) { tracked, active ->
                resolvePlaylistRefreshStatus(
                    launchPhase = applicableLaunch.phase,
                    trackedWorkState = tracked?.state,
                    activeWorkState = active?.state,
                )
            }
        }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            initialValue = PlaylistRefreshStatus.IDLE,
            started = SharingStarted.Eagerly,
        )

    private companion object {
        const val PLAYLIST_REMOVAL_STATE_KEY = "playlist_removal_state"
    }
}

private sealed interface RefreshLaunch {
    val playlistUrl: String?
    val phase: RefreshLaunchPhase

    data object Idle : RefreshLaunch {
        override val playlistUrl: String? = null
        override val phase = RefreshLaunchPhase.IDLE
    }

    data class Enqueuing(
        override val playlistUrl: String,
    ) : RefreshLaunch {
        override val phase = RefreshLaunchPhase.ENQUEUING
    }

    data class Work(
        override val playlistUrl: String,
        val workId: UUID,
    ) : RefreshLaunch {
        override val phase = RefreshLaunchPhase.WORK
    }

    data class Failed(
        override val playlistUrl: String,
    ) : RefreshLaunch {
        override val phase = RefreshLaunchPhase.FAILED
    }
}

private inline fun MutableStateFlow<RefreshLaunch>.updateFor(
    playlistUrl: String,
    update: () -> RefreshLaunch,
) {
    if (value.playlistUrl == playlistUrl) {
        value = update()
    }
}

private fun List<WorkInfo>.findPlaylistWork(
    playlist: Playlist?,
    includeProgrammeRefresh: Boolean,
): WorkInfo? {
    val playlistUrl = playlist?.url ?: return null
    val workTag = playlistWorkTag(playlistUrl)
    return find { info ->
        val isProgrammeRefresh = DataSource.EPG.value in info.tags
        SubscriptionWorker.TAG in info.tags &&
            isProgrammeRefresh == includeProgrammeRefresh &&
            (
                workTag in info.tags ||
                    // Compatibility with work enqueued before hashed tags shipped.
                    playlistUrl in info.tags
                )
    }
}
