package com.m3u.features.foryou

import androidx.compose.runtime.snapshotFlow
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
import com.m3u.core.architecture.preferences.Preferences
import com.m3u.core.wrapper.Resource
import com.m3u.core.wrapper.asResource
import com.m3u.core.wrapper.mapResource
import com.m3u.core.wrapper.resource
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.PlaylistWithCount
import com.m3u.data.database.model.Channel
import com.m3u.data.parser.xtream.XtreamChannelInfo
import com.m3u.data.repository.playlist.PlaylistRepository
import com.m3u.data.repository.programme.ProgrammeRepository
import com.m3u.data.repository.channel.ChannelRepository
import com.m3u.data.worker.SubscriptionWorker
import com.m3u.features.foryou.components.recommend.Recommend
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@HiltViewModel
class ForyouViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    channelRepository: ChannelRepository,
    programmeRepository: ProgrammeRepository,
    preferences: Preferences,
    @Dispatcher(IO) ioDispatcher: CoroutineDispatcher,
    workManager: WorkManager,
    delegate: Logger
) : ViewModel() {
    private val logger = delegate.install(Profiles.VIEWMODEL_FORYOU)

    internal val playlistCountsResource: StateFlow<Resource<List<PlaylistWithCount>>> =
        playlistRepository
            .observeAllCounts()
            .asResource()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Lazily,
                initialValue = Resource.Loading
            )

    internal val subscribingPlaylistUrls: StateFlow<List<String>> =
        workManager.getWorkInfosFlow(
            WorkQuery.fromStates(
                WorkInfo.State.RUNNING,
                WorkInfo.State.ENQUEUED,
            )
        )
            .combine(playlistRepository.observePlaylistUrls()) { infos, playlistUrls ->
                infos
                    .filter { info -> SubscriptionWorker.TAG in info.tags }
                    .mapNotNull { info -> info.tags.firstOrNull { it in playlistUrls } }

            }
            .stateIn(
                scope = viewModelScope,
                initialValue = emptyList(),
                started = SharingStarted.WhileSubscribed(5_000L)
            )

    internal val refreshingEpgUrls: Flow<List<String>> = programmeRepository.refreshingEpgUrls

    private val unseensDuration = snapshotFlow { preferences.unseensMilliseconds }
        .map { it.toDuration(DurationUnit.MILLISECONDS) }
        .flowOn(ioDispatcher)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = Duration.INFINITE
        )

    internal val recommend: StateFlow<Recommend> = unseensDuration
        .flatMapLatest { channelRepository.observeAllUnseenFavourites(it) }
        .map { prev -> Recommend(prev.map { Recommend.UnseenSpec(it) }) }
        .flowOn(ioDispatcher)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = Recommend()
        )

    internal fun onUnsubscribePlaylist(url: String) {
        viewModelScope.launch {
            playlistRepository.unsubscribe(url)
        }
    }

    internal val series = MutableStateFlow<Channel?>(null)
    internal val seriesReplay = MutableStateFlow(0)
    internal val episodes: StateFlow<Resource<List<XtreamChannelInfo.Episode>>> = series
        .combine(seriesReplay) { series, _ -> series }
        .flatMapLatest { series ->
            if (series == null) flow { }
            else resource { playlistRepository.readEpisodesOrThrow(series) }
                .mapResource { it }
        }
        .stateIn(
            scope = viewModelScope,
            initialValue = Resource.Loading,
            // don't lose
            started = SharingStarted.Lazily
        )

    internal suspend fun getPlaylist(playlistUrl: String): Playlist? =
        playlistRepository.get(playlistUrl)
}
