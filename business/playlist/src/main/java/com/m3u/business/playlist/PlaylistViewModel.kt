package com.m3u.business.playlist

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Immutable
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
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import com.m3u.business.playlist.PlaylistMessage.ChannelCoverSaved
import com.m3u.core.Contracts
import com.m3u.core.architecture.preferences.PreferencesKeys
import com.m3u.core.architecture.preferences.Settings
import com.m3u.core.architecture.preferences.flowOf
import com.m3u.core.util.coroutine.flatmapCombined
import com.m3u.core.wrapper.Event
import com.m3u.core.wrapper.Resource
import com.m3u.core.wrapper.Sort
import com.m3u.core.wrapper.handledEvent
import com.m3u.core.wrapper.mapResource
import com.m3u.core.wrapper.resource
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.Programme
import com.m3u.data.database.model.isSeries
import com.m3u.data.parser.xtream.XtreamChannelInfo
import com.m3u.data.repository.channel.ChannelRepository
import com.m3u.data.repository.media.MediaRepository
import com.m3u.data.repository.playlist.PlaylistRepository
import com.m3u.data.repository.programme.ProgrammeRepository
import com.m3u.data.service.MediaCommand
import com.m3u.data.service.Messager
import com.m3u.data.service.PlayerManager
import com.m3u.data.worker.SubscriptionWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val channelRepository: ChannelRepository,
    private val playlistRepository: PlaylistRepository,
    private val mediaRepository: MediaRepository,
    private val programmeRepository: ProgrammeRepository,
    private val messager: Messager,
    private val playerManager: PlayerManager,
    settings: Settings,
    workManager: WorkManager,
) : ViewModel() {
    private val timber = Timber.tag("PlaylistViewModel")

    val playlistUrl: StateFlow<String> = savedStateHandle
        .getStateFlow(PlaylistNavigation.TYPE_URL, "")

    val playlist: StateFlow<Playlist?> = playlistUrl.flatMapLatest {
        playlistRepository.observe(it)
    }
        .stateIn(
            scope = viewModelScope,
            initialValue = null,
            started = SharingStarted.WhileSubscribed(5_000L)
        )

    val zapping: StateFlow<Channel?> = combine(
        settings.flowOf(PreferencesKeys.ZAPPING_MODE),
        playerManager.channel,
        playlistUrl.flatMapLatest { channelRepository.observeAllByPlaylistUrl(it) }
    ) { zappingMode, channel, channels ->
        if (!zappingMode) null
        else channels.find { it.url == channel?.url }
    }
        .stateIn(
            scope = viewModelScope,
            initialValue = null,
            started = SharingStarted.WhileSubscribed(5_000)
        )

    val subscribingOrRefreshing: StateFlow<Boolean> = workManager
        .getWorkInfosFlow(
            WorkQuery.fromStates(
                WorkInfo.State.RUNNING,
                WorkInfo.State.ENQUEUED,
            )
        )
        .combine(playlistUrl) { infos, playlistUrl ->
            infos.any { info ->
                info.tags.containsAll(
                    listOf(SubscriptionWorker.TAG, playlistUrl)
                )
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            initialValue = false,
            started = SharingStarted.WhileSubscribed(5000)
        )

    fun refresh() {
        val url = playlistUrl.value
        viewModelScope.launch {
            playlistRepository.refresh(url)
        }
    }

    fun favourite(id: Int) {
        viewModelScope.launch {
            channelRepository.favouriteOrUnfavourite(id)
        }
    }

    fun savePicture(id: Int) {
        viewModelScope.launch {
            val channel = channelRepository.get(id)
            if (channel == null) {
                messager.emit(PlaylistMessage.ChannelNotFound)
                return@launch
            }
            val cover = channel.cover
            if (cover.isNullOrEmpty()) {
                messager.emit(PlaylistMessage.ChannelCoverNotFound)
                return@launch
            }
            resource { mediaRepository.savePicture(cover) }
                .onEach { resource ->
                    when (resource) {
                        Resource.Loading -> {}
                        is Resource.Success -> {
                            messager.emit(ChannelCoverSaved(resource.data.absolutePath))
                        }

                        is Resource.Failure -> {
                            messager.emit(resource.message.orEmpty())
                        }
                    }
                }
                .launchIn(this)
        }
    }

    fun hide(id: Int) {
        viewModelScope.launch {
            val channel = channelRepository.get(id)
            if (channel == null) {
                messager.emit(PlaylistMessage.ChannelNotFound)
            } else {
                channelRepository.hide(channel.id, true)
            }
        }
    }

    fun createShortcut(context: Context, id: Int) {
        val shortcutId = "channel_$id"
        viewModelScope.launch {
            val channel = channelRepository.get(id) ?: return@launch
            val bitmap = channel.cover?.let { mediaRepository.loadDrawable(it)?.toBitmap() }
            val shortcutInfo = ShortcutInfoCompat.Builder(context, shortcutId)
                .setShortLabel(channel.title)
                .setLongLabel(channel.url)
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
                        putExtra(Contracts.PLAYER_SHORTCUT_CHANNEL_ID, channel.id)
                    }
                )
                .build()
            ShortcutManagerCompat.pushDynamicShortcut(context, shortcutInfo)
        }
    }

    suspend fun getProgrammeCurrently(channelId: Int): Programme? {
        return programmeRepository.getProgrammeCurrently(channelId)
    }

    private val sortIndex: MutableStateFlow<Int> = MutableStateFlow(0)

    val sort: StateFlow<Sort> = sortIndex
        .map { Sort.entries[it] }
        .stateIn(
            scope = viewModelScope,
            initialValue = Sort.UNSPECIFIED,
            started = SharingStarted.WhileSubscribed(5_000L)
        )

    fun sort(sort: Sort) {
        sortIndex.value = Sort.entries.indexOf(sort).coerceAtLeast(0)
    }

    val query = MutableStateFlow("")
    val scrollUp: MutableStateFlow<Event<Unit>> = MutableStateFlow(handledEvent())

    @Immutable
    data class ChannelParameters(
        val playlistUrl: String,
        val query: String,
        val sort: Sort,
        val categories: List<String>,
    )

    @OptIn(FlowPreview::class)
    private val categories: StateFlow<List<String>> =
        flatmapCombined(playlistUrl, query, sort) { playlistUrl, query, sort ->
            if (sort == Sort.MIXED) flowOf(emptyList())
            else playlistRepository.observeCategoriesByPlaylistUrlIgnoreHidden(playlistUrl, query)
        }
            .let { flow ->
                merge(
                    flow.take(1),
                    flow.drop(1).debounce(1.seconds)
                )
            }
            .stateIn(
                scope = viewModelScope,
                initialValue = emptyList(),
                started = SharingStarted.Lazily
            )

    val channels: StateFlow<Map<String, Flow<PagingData<Channel>>>> = combine(
        playlistUrl,
        categories,
        query, sort
    ) { playlistUrl, categories, query, sort ->
        ChannelParameters(
            playlistUrl = playlistUrl,
            query = query,
            sort = sort,
            categories = categories
        )
    }
        .mapLatest { (playlistUrl, query, sort, categories) ->
            if (sort == Sort.MIXED) {
                mapOf(
                    "" to Pager(PagingConfig(15)) {
                        channelRepository.pagingAllByPlaylistUrl(
                            playlistUrl,
                            "",
                            query,
                            sort
                        )
                    }
                        .flow
                        .cachedIn(viewModelScope)
                )
            } else {
                categories.associate { category ->
                    category to Pager(PagingConfig(15)) {
                        channelRepository.pagingAllByPlaylistUrl(
                            playlistUrl,
                            category,
                            query,
                            sort
                        )
                    }
                        .flow
                        .cachedIn(viewModelScope)
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            initialValue = emptyMap(),
            started = SharingStarted.Lazily
        )

    val pinnedCategories: StateFlow<List<String>> = playlist
        .map { it?.pinnedCategories ?: emptyList() }
        .stateIn(
            scope = viewModelScope,
            initialValue = emptyList(),
            started = SharingStarted.WhileSubscribed(5_000L)
        )

    fun onPinOrUnpinCategory(category: String) {
        val currentPlaylistUrl = playlistUrl.value
        viewModelScope.launch {
            playlistRepository.pinOrUnpinCategory(currentPlaylistUrl, category)
        }
    }

    fun onHideCategory(category: String) {
        val currentPlaylistUrl = playlistUrl.value
        viewModelScope.launch {
            playlistRepository.hideOrUnhideCategory(currentPlaylistUrl, category)
        }
    }

    fun setup(
        channelId: Int,
        onPlayMediaCommand: (MediaCommand) -> Unit
    ) {
        viewModelScope.launch {
            val channel = channelRepository.get(channelId) ?: return@launch
            val playlist = playlistRepository.get(channel.playlistUrl)
            savedStateHandle[PlaylistNavigation.TYPE_URL] = channel.playlistUrl

            if (playlist?.isSeries == false) {
                onPlayMediaCommand(MediaCommand.Common(channel.id))
            } else {
                series.value = channel
            }
        }
    }

    suspend fun reloadThumbnail(channelUrl: String): Uri? {
        return playerManager.reloadThumbnail(channelUrl)
    }

    suspend fun syncThumbnail(channelUrl: String): Uri? {
        return playerManager.syncThumbnail(channelUrl)
    }

    val series = MutableStateFlow<Channel?>(null)
    val seriesReplay = MutableStateFlow(0)

    val episodes: StateFlow<Resource<List<XtreamChannelInfo.Episode>>> = series
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
