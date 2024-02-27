package com.m3u.features.playlist

import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import com.m3u.core.Contracts
import com.m3u.core.architecture.dispatcher.Dispatcher
import com.m3u.core.architecture.dispatcher.M3uDispatchers.IO
import com.m3u.core.architecture.pref.Pref
import com.m3u.core.architecture.pref.observeAsFlow
import com.m3u.core.architecture.viewmodel.BaseViewModel
import com.m3u.core.wrapper.Message
import com.m3u.core.wrapper.Resource
import com.m3u.core.wrapper.eventOf
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.Stream
import com.m3u.data.repository.MediaRepository
import com.m3u.data.repository.PlaylistRepository
import com.m3u.data.repository.StreamRepository
import com.m3u.data.service.Messager
import com.m3u.data.service.PlayerManager
import com.m3u.data.worker.SubscriptionWorker
import com.m3u.features.playlist.PlaylistMessage.StreamCoverSaved
import com.m3u.features.playlist.navigation.PlaylistNavigation
import com.m3u.ui.Sort
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.tvprovider.media.tv.Channel as TvChannel

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val streamRepository: StreamRepository,
    private val playlistRepository: PlaylistRepository,
    private val mediaRepository: MediaRepository,
    private val messager: Messager,
    playerManager: PlayerManager,
    pref: Pref,
    workManager: WorkManager,
    @Dispatcher(IO) ioDispatcher: CoroutineDispatcher
) : BaseViewModel<PlaylistState, PlaylistEvent>(
    emptyState = PlaylistState()
) {
    internal val playlistUrl: StateFlow<String> = savedStateHandle
        .getStateFlow(PlaylistNavigation.TYPE_URL, "")
    internal val dataSourceType: StateFlow<String> = savedStateHandle
        .getStateFlow(PlaylistNavigation.TYPE_DATA_SOURCE_TYPE, "")

    override fun onEvent(event: PlaylistEvent) {
        when (event) {
            PlaylistEvent.Refresh -> refresh()
            is PlaylistEvent.Favourite -> favourite(event)
            PlaylistEvent.ScrollUp -> scrollUp()
            is PlaylistEvent.Hide -> hide(event)
            is PlaylistEvent.SavePicture -> savePicture(event)
            is PlaylistEvent.Query -> query(event)
            is PlaylistEvent.CreateShortcut -> createShortcut(event.context, event.id)
            is PlaylistEvent.CreateTvRecommend -> createTvRecommend(event.contentResolver, event.id)
        }
    }

    internal val zapping: StateFlow<Stream?> = combine(
        pref.observeAsFlow { it.zappingMode },
        playerManager.url,
        streamRepository.observeAll()
    ) { zappingMode, url, streams ->
        if (!zappingMode) null
        else streams.find { it.url == url }
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
                WorkInfo.State.ENQUEUED
            )
        )
        .mapLatest { infos ->
            infos.any { info -> SubscriptionWorker.TAG in info.tags }
        }
        .flowOn(ioDispatcher)
        .onEach { refreshing ->
            messager.emit(
                if (refreshing) PlaylistMessage.Refreshing else Message.Dynamic.EMPTY
            )
        }
        .stateIn(
            scope = viewModelScope,
            initialValue = false,
            started = SharingStarted.WhileSubscribed(5000)
        )

    private fun refresh() {
        val url = playlistUrl.value
        viewModelScope.launch {
            playlistRepository.refresh(url)
        }
    }

    private fun favourite(event: PlaylistEvent.Favourite) {
        viewModelScope.launch {
            val id = event.id
            val target = event.target
            streamRepository.setFavourite(id, target)
        }
    }

    private fun scrollUp() {
        writable.update {
            it.copy(
                scrollUp = eventOf(Unit)
            )
        }
    }

    private fun savePicture(event: PlaylistEvent.SavePicture) {
        val id = event.id
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
            mediaRepository
                .savePicture(cover)
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

    private fun hide(event: PlaylistEvent.Hide) {
        viewModelScope.launch {
            val id = event.id
            val stream = streamRepository.get(id)
            if (stream == null) {
                messager.emit(PlaylistMessage.StreamNotFound)
            } else {
                streamRepository.hide(stream.id, true)
            }
        }
    }

    private fun createShortcut(context: Context, id: Int) {
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
                        putExtra(Contracts.PLAYER_SHORTCUT_STREAM_URL, stream.url)
                    }
                )
                .build()
            ShortcutManagerCompat.pushDynamicShortcut(context, shortcutInfo)
        }
    }

    private fun createTvRecommend(contentResolver: ContentResolver, id: Int) {
        viewModelScope.launch {
            val stream = streamRepository.get(id) ?: return@launch
            val bitmap = stream.cover?.let { mediaRepository.loadDrawable(it)?.toBitmap() }
            val channel = TvChannel.Builder()
                .setType(TvContractCompat.Channels.TYPE_PREVIEW)
                .setDisplayName(stream.title)
                .setInternalProviderId(id.toString())
                .setAppLinkIntentUri("content://channelsample.com/category/$id".toUri())
                .build()
            contentResolver.insert(
                TvContractCompat.Channels.CONTENT_URI,
                channel.toContentValues()
            )
        }
    }

    private val _query: MutableStateFlow<String> = MutableStateFlow("")
    internal val query: StateFlow<String> = _query.asStateFlow()
    private fun query(event: PlaylistEvent.Query) {
        val text = event.text
        _query.update { text }
    }

    private fun List<Stream>.toChannels(): List<Group> = groupBy { it.group }
        .toList()
        .map { Group(it.first, it.second.toPersistentList()) }

    private fun List<Stream>.toSingleChannel(): List<Group> = listOf(
        Group("", toPersistentList())
    )

    internal val playlist: StateFlow<Playlist?> = playlistUrl.map { url ->
        playlistRepository.get(url)
    }
        .stateIn(
            scope = viewModelScope,
            initialValue = null,
            started = SharingStarted.WhileSubscribed(5_000L)
        )

    private val unsorted: StateFlow<List<Stream>> = combine(
        playlistUrl.flatMapLatest { url ->
            playlistRepository.observeWithStreams(url)
        },
        query
    ) { current, query ->
        current?.streams?.filter { !it.hidden && it.title.contains(query, true) } ?: emptyList()
    }
        .stateIn(
            scope = viewModelScope,
            initialValue = emptyList(),
            started = SharingStarted.WhileSubscribed(5_000L)
        )

    internal val sorts: ImmutableList<Sort> = Sort.entries.toPersistentList()

    private val sortIndex: MutableStateFlow<Int> = MutableStateFlow(0)

    internal val sort: StateFlow<Sort> = sortIndex
        .map { sorts[it] }
        .stateIn(
            scope = viewModelScope,
            initialValue = Sort.UNSPECIFIED,
            started = SharingStarted.WhileSubscribed(5_000L)
        )

    internal fun sort(sort: Sort) {
        sortIndex.update { sorts.indexOf(sort).coerceAtLeast(0) }
    }

    internal val channels: StateFlow<ImmutableList<Group>> = combine(
        unsorted,
        sort
    ) { all, sort ->
        when (sort) {
            Sort.ASC -> all.sortedWith(
                compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
            ).toSingleChannel()

            Sort.DESC -> all.sortedWith(
                compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.title }
            ).toSingleChannel()

            Sort.RECENTLY -> all.sortedByDescending { it.seen }.toSingleChannel()
            Sort.UNSPECIFIED -> all.toChannels()
        }
            .toPersistentList()
    }
        .stateIn(
            scope = viewModelScope,
            initialValue = persistentListOf(),
            started = SharingStarted.WhileSubscribed(5_000L)
        )
}