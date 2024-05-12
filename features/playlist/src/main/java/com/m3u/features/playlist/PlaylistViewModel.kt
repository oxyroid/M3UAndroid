package com.m3u.features.playlist

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.snapshotFlow
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import com.m3u.core.Contracts
import com.m3u.core.architecture.dispatcher.Dispatcher
import com.m3u.core.architecture.dispatcher.M3uDispatchers.IO
import com.m3u.core.architecture.logger.Logger
import com.m3u.core.architecture.logger.Profiles
import com.m3u.core.architecture.logger.install
import com.m3u.core.architecture.preferences.Preferences
import com.m3u.core.wrapper.Event
import com.m3u.core.wrapper.Resource
import com.m3u.core.wrapper.handledEvent
import com.m3u.core.wrapper.mapResource
import com.m3u.core.wrapper.resource
import com.m3u.data.database.dao.ProgrammeDao
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.Programme
import com.m3u.data.database.model.Stream
import com.m3u.data.database.model.epgUrlsOrXtreamXmlUrl
import com.m3u.data.parser.xtream.XtreamStreamInfo
import com.m3u.data.repository.media.MediaRepository
import com.m3u.data.repository.playlist.PlaylistRepository
import com.m3u.data.repository.stream.StreamRepository
import com.m3u.data.service.Messager
import com.m3u.data.service.PlayerManager
import com.m3u.data.worker.SubscriptionWorker
import com.m3u.features.playlist.PlaylistMessage.StreamCoverSaved
import com.m3u.features.playlist.navigation.PlaylistNavigation
import com.m3u.ui.Sort
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
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import javax.inject.Inject

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val streamRepository: StreamRepository,
    private val playlistRepository: PlaylistRepository,
    private val mediaRepository: MediaRepository,
    private val programmeDao: ProgrammeDao,
    private val messager: Messager,
    playerManager: PlayerManager,
    preferences: Preferences,
    workManager: WorkManager,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
    delegate: Logger
) : ViewModel() {
    private val logger = delegate.install(Profiles.VIEWMODEL_PLAYLIST)

    internal val playlistUrl: StateFlow<String> = savedStateHandle
        .getStateFlow(PlaylistNavigation.TYPE_URL, "")

    internal val playlist: StateFlow<Playlist?> = playlistUrl.flatMapLatest {
        playlistRepository.observe(it)
    }
        .stateIn(
            scope = viewModelScope,
            initialValue = null,
            started = SharingStarted.WhileSubscribed(5_000L)
        )

    internal val zapping: StateFlow<Stream?> = combine(
        snapshotFlow { preferences.zappingMode },
        playerManager.stream,
        playlistUrl.flatMapLatest { streamRepository.observeAllByPlaylistUrl(it) }
    ) { zappingMode, stream, streams ->
        if (!zappingMode) null
        else streams.find { it.url == stream?.url }
    }
        .stateIn(
            scope = viewModelScope,
            initialValue = null,
            started = SharingStarted.WhileSubscribed(5_000)
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

    internal fun refresh() {
        val url = playlistUrl.value
        viewModelScope.launch {
            playlistRepository.refresh(url)
        }
    }

    internal fun favourite(id: Int) {
        viewModelScope.launch {
            streamRepository.favouriteOrUnfavourite(id)
        }
    }

    internal fun savePicture(id: Int) {
        viewModelScope.launch {
            val stream = streamRepository.get(id)
            if (stream == null) {
                messager.emit(PlaylistMessage.StreamNotFound)
                return@launch
            }
            val cover = stream.cover
            if (cover.isNullOrEmpty()) {
                messager.emit(PlaylistMessage.StreamCoverNotFound)
                return@launch
            }
            resource { mediaRepository.savePicture(cover) }
                .onEach { resource ->
                    when (resource) {
                        Resource.Loading -> {}
                        is Resource.Success -> {
                            messager.emit(StreamCoverSaved(resource.data.absolutePath))
                        }

                        is Resource.Failure -> {
                            messager.emit(resource.message.orEmpty())
                        }
                    }
                }
                .launchIn(this)
        }
    }

    internal fun hide(id: Int) {
        viewModelScope.launch {
            val stream = streamRepository.get(id)
            if (stream == null) {
                messager.emit(PlaylistMessage.StreamNotFound)
            } else {
                streamRepository.hide(stream.id, true)
            }
        }
    }

    internal fun createShortcut(context: Context, id: Int) {
        val shortcutId = "stream_$id"
        viewModelScope.launch {
            val stream = streamRepository.get(id) ?: return@launch
            val bitmap = stream.cover?.let { mediaRepository.loadDrawable(it)?.toBitmap() }
            val shortcutInfo = ShortcutInfoCompat.Builder(context, shortcutId)
                .setShortLabel(stream.title)
                .setLongLabel(stream.url)
                .setIcon(
                    bitmap
                        ?.let { IconCompat.createWithBitmap(it) }
                        ?: IconCompat.createWithResource(context, R.drawable.round_play_arrow_24)
                )
                .setIntent(
                    Intent(Intent.ACTION_VIEW).apply {
                        component = ComponentName.createRelative(
                            context,
                            Contracts.PLAYER_ACTIVITY
                        )
                        putExtra(Contracts.PLAYER_SHORTCUT_STREAM_ID, stream.id)
                    }
                )
                .build()
            ShortcutManagerCompat.pushDynamicShortcut(context, shortcutInfo)
        }
    }

    internal fun createTvRecommend(context: Context, id: Int) {
    }

    internal suspend fun getProgrammeCurrently(channelId: String): Programme? {
        val playlist = playlist.value ?: return null
        val epgUrls = playlist.epgUrlsOrXtreamXmlUrl()
        if (epgUrls.isEmpty()) return null
        val time = Clock.System.now().toEpochMilliseconds()
        return programmeDao.getCurrentByEpgUrlsAndChannelId(
            epgUrls = epgUrls,
            channelId = channelId,
            time = time
        )
    }

    private val sortIndex: MutableStateFlow<Int> = MutableStateFlow(0)

    internal val sort: StateFlow<Sort> = sortIndex
        .map { Sort.entries[it] }
        .stateIn(
            scope = viewModelScope,
            initialValue = Sort.UNSPECIFIED,
            started = SharingStarted.WhileSubscribed(5_000L)
        )

    internal fun sort(sort: Sort) {
        sortIndex.value = Sort.entries.indexOf(sort).coerceAtLeast(0)
    }

    internal val query = MutableStateFlow("")
    internal val scrollUp: MutableStateFlow<Event<Unit>> = MutableStateFlow(handledEvent())

    data class PagingStreamParameters(
        val playlistUrl: String,
        val query: String,
        val sort: Sort,
        val categories: List<String>
    )

    data class Channel(
        val category: String,
        val streams: Flow<PagingData<Stream>>
    )

    internal val channels: StateFlow<List<Channel>> = combine(
        playlistUrl,
        query,
        sort
    ) { playlistUrl, query, sort ->
        val categories = playlistRepository.getCategoriesByPlaylistUrlIgnoreHidden(playlistUrl)
        PagingStreamParameters(
            playlistUrl = playlistUrl,
            query = query,
            sort = sort,
            categories = categories
        )
    }
        .map { (playlistUrl, query, sort, categories) ->
            categories.map { category ->
                Channel(
                    category = category,
                    streams = Pager(PagingConfig(15)) {
                        streamRepository.pagingAllByPlaylistUrl(
                            playlistUrl,
                            category,
                            query,
                            when (sort) {
                                Sort.UNSPECIFIED -> StreamRepository.Sort.UNSPECIFIED
                                Sort.ASC -> StreamRepository.Sort.ASC
                                Sort.DESC -> StreamRepository.Sort.DESC
                                Sort.RECENTLY -> StreamRepository.Sort.RECENTLY
                            }
                        )
                    }
                        .flow
                        .map { data -> data.filter { !it.hidden } }
                        .flowOn(ioDispatcher)
                        .cachedIn(viewModelScope)
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            initialValue = emptyList(),
            started = SharingStarted.WhileSubscribed(5_000L)
        )

    internal val pinnedCategories: StateFlow<List<String>> = playlist
        .map { it?.pinnedCategories ?: emptyList() }

        .flowOn(ioDispatcher)
        .stateIn(
            scope = viewModelScope,
            initialValue = emptyList(),
            started = SharingStarted.WhileSubscribed(5_000L)
        )

    internal fun pinOrUnpinCategory(category: String) {
        val currentPlaylistUrl = playlistUrl.value
        viewModelScope.launch {
            playlistRepository.pinOrUnpinCategory(currentPlaylistUrl, category)
        }
    }

    internal fun hideCategory(category: String) {
        val currentPlaylistUrl = playlistUrl.value
        viewModelScope.launch {
            playlistRepository.hideOrUnhideCategory(currentPlaylistUrl, category)
        }
    }

    internal val series = MutableStateFlow<Stream?>(null)
    internal val seriesReplay = MutableStateFlow(0)

    internal val episodes: StateFlow<Resource<List<XtreamStreamInfo.Episode>>> = series
        .combine(seriesReplay) { series, _ -> series }
        .flatMapLatest { series ->
            if (series == null) flow {}
            else resource { playlistRepository.readEpisodesOrThrow(series) }
                .mapResource { it }
        }
        .stateIn(
            scope = viewModelScope,
            initialValue = Resource.Loading,
            // don't lose
            started = SharingStarted.Lazily
        )
}
