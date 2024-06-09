package com.m3u.feature.favorite

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.snapshotFlow
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m3u.core.Contracts
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
import com.m3u.data.database.model.Channel
import com.m3u.data.parser.xtream.XtreamChannelInfo
import com.m3u.data.repository.media.MediaRepository
import com.m3u.data.repository.playlist.PlaylistRepository
import com.m3u.data.repository.channel.ChannelRepository
import com.m3u.data.service.MediaCommand
import com.m3u.data.service.PlayerManager
import com.m3u.ui.Sort
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val channelRepository: ChannelRepository,
    private val mediaRepository: MediaRepository,
    private val playerManager: PlayerManager,
    preferences: Preferences,
    @Dispatcher(IO) ioDispatcher: CoroutineDispatcher,
    delegate: Logger
) : ViewModel() {
    private val logger = delegate.install(Profiles.VIEWMODEL_FAVOURITE)

    private val zappingMode = snapshotFlow { preferences.zappingMode }
        .flowOn(ioDispatcher)
        .stateIn(
            scope = viewModelScope,
            initialValue = Preferences.DEFAULT_ZAPPING_MODE,
            started = SharingStarted.WhileSubscribed(5_000)
        )

    val zapping: StateFlow<Channel?> = combine(
        zappingMode,
        playerManager.channel
    ) { zappingMode, channel ->
        channel.takeIf { zappingMode }
    }
        .flowOn(ioDispatcher)
        .stateIn(
            scope = viewModelScope,
            initialValue = null,
            started = SharingStarted.WhileSubscribed(5_000)
        )

    val sorts: List<Sort> = Sort.entries

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

    val channels: StateFlow<Resource<List<Channel>>> = channelRepository
        .observeAllFavourite()
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

        }
        .asResource()
        .flowOn(ioDispatcher)
        .stateIn(
            scope = viewModelScope,
            initialValue = Resource.Loading,
            started = SharingStarted.WhileSubscribed(5_000L)
        )

    fun favourite(id: Int) {
        viewModelScope.launch {
            channelRepository.favouriteOrUnfavourite(id)
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

    internal fun playRandomly() {
        viewModelScope.launch {
            val channel = channelRepository.getRandomIgnoreSeriesAndHidden() ?: return@launch
            playerManager.play(
                MediaCommand.Common(channel.id)
            )
        }
    }
}
