package com.m3u.features.favorite

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m3u.core.Contracts
import com.m3u.core.architecture.dispatcher.Dispatcher
import com.m3u.core.architecture.dispatcher.M3uDispatchers.IO
import com.m3u.core.architecture.pref.Pref
import com.m3u.core.architecture.pref.observeAsFlow
import com.m3u.core.wrapper.Resource
import com.m3u.core.wrapper.flattenResource
import com.m3u.core.wrapper.mapResource
import com.m3u.core.wrapper.resource
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.Stream
import com.m3u.data.parser.xtream.XtreamStreamInfo
import com.m3u.data.repository.MediaRepository
import com.m3u.data.repository.PlaylistRepository
import com.m3u.data.repository.StreamRepository
import com.m3u.data.repository.observeAll
import com.m3u.data.service.PlayerManagerV2
import com.m3u.ui.Sort
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FavouriteViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val streamRepository: StreamRepository,
    private val mediaRepository: MediaRepository,
    pref: Pref,
    private val playerManager: PlayerManagerV2,
    @Dispatcher(IO) ioDispatcher: CoroutineDispatcher
) : ViewModel() {
    private val zappingMode = pref
        .observeAsFlow { it.zappingMode }
        .flowOn(ioDispatcher)
        .stateIn(
            scope = viewModelScope,
            initialValue = Pref.DEFAULT_ZAPPING_MODE,
            started = SharingStarted.WhileSubscribed(5_000)
        )

    val zapping: StateFlow<Stream?> = combine(
        zappingMode,
        playerManager.stream
    ) { zappingMode, stream ->
        stream.takeIf { zappingMode }
    }
        .flowOn(ioDispatcher)
        .stateIn(
            scope = viewModelScope,
            initialValue = null,
            started = SharingStarted.WhileSubscribed(5_000)
        )

    val sorts: ImmutableList<Sort> = Sort.entries.toPersistentList()

    private val sortIndex = MutableStateFlow(0)

    val sort = sortIndex
        .map { sorts[it] }
        .flowOn(ioDispatcher)
        .stateIn(
            scope = viewModelScope,
            initialValue = Sort.UNSPECIFIED,
            started = SharingStarted.WhileSubscribed(5_000L)
        )

    fun sort(sort: Sort) {
        sortIndex.update { sorts.indexOf(sort).coerceAtLeast(0) }
    }

    val streamsResource: StateFlow<Resource<ImmutableList<Stream>>> = flattenResource {
        streamRepository
            .observeAll { it.favourite }
            .combine(sort) { all, sort ->
                when (sort) {
                    Sort.UNSPECIFIED -> all
                    Sort.ASC -> all.sortedWith(
                        compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
                    )

                    Sort.DESC -> all.sortedWith(
                        compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.title }
                    )

                    Sort.RECENTLY -> all.sortedByDescending { it.seen }
                }
                    .toPersistentList()
            }
    }
        .flowOn(ioDispatcher)
        .stateIn(
            scope = viewModelScope,
            initialValue = Resource.Loading,
            started = SharingStarted.WhileSubscribed(5_000L)
        )

    fun cancelFavourite(id: Int) {
        viewModelScope.launch {
            streamRepository.setFavourite(id, false)
        }
    }

    fun createShortcut(context: Context, id: Int) {
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

    private val series = MutableStateFlow<Stream?>(null)
    internal val episodes: StateFlow<Resource<ImmutableList<XtreamStreamInfo.Episode>>> = series
        .flatMapLatest { series ->
            if (series == null) flow { }
            else resource { playlistRepository.readEpisodesOrThrow(series) }
                .mapResource { it.toPersistentList() }
        }
        .stateIn(
            scope = viewModelScope,
            initialValue = Resource.Loading,
            started = SharingStarted.WhileSubscribed(5_000L)
        )

    internal fun onRequestEpisodes(series: Stream) {
        this.series.value = series
    }

    internal fun onClearEpisodes() {
        this.series.value = null
    }

    internal suspend fun getPlaylist(playlistUrl: String): Playlist? =
        playlistRepository.get(playlistUrl)
}
