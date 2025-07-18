package com.m3u.business.favorite

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.m3u.core.Contracts
import com.m3u.core.architecture.preferences.PreferencesKeys
import com.m3u.core.architecture.preferences.Settings
import com.m3u.core.architecture.preferences.flowOf
import com.m3u.core.wrapper.Resource
import com.m3u.core.wrapper.Sort
import com.m3u.core.wrapper.mapResource
import com.m3u.core.wrapper.resource
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.Playlist
import com.m3u.data.parser.xtream.XtreamChannelInfo
import com.m3u.data.repository.channel.ChannelRepository
import com.m3u.data.repository.media.MediaRepository
import com.m3u.data.repository.playlist.PlaylistRepository
import com.m3u.data.service.PlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FavoriteViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val channelRepository: ChannelRepository,
    private val mediaRepository: MediaRepository,
    private val playerManager: PlayerManager,
    settings: Settings,
) : ViewModel() {
    val zapping: StateFlow<Channel?> = combine(
        settings.flowOf(PreferencesKeys.ZAPPING_MODE),
        playerManager.channel
    ) { zappingMode, channel ->
        channel.takeIf { zappingMode }
    }
        .stateIn(
            scope = viewModelScope,
            initialValue = null,
            started = SharingStarted.WhileSubscribed(5_000)
        )

    val sorts: List<Sort> = listOf(
        Sort.UNSPECIFIED,
        Sort.ASC,
        Sort.DESC,
        Sort.RECENTLY,
        // mixed channels is not support for favourite gallery.
        // Sort.MIXED
    )

    private val sortIndex = MutableStateFlow(0)

    val sort = sortIndex
        .map { sorts[it] }
        .stateIn(
            scope = viewModelScope,
            initialValue = Sort.UNSPECIFIED,
            started = SharingStarted.WhileSubscribed(5_000L)
        )

    fun sort(sort: Sort) {
        sortIndex.update { sorts.indexOf(sort).coerceAtLeast(0) }
    }

    val channels: Flow<PagingData<Channel>> = sort.flatMapLatest {
        Pager(PagingConfig(10)) {
            channelRepository.pagingAllFavorite(it)
        }
            .flow
    }
        .cachedIn(viewModelScope)

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

    val series = MutableStateFlow<Channel?>(null)
    val seriesReplay = MutableStateFlow(0)
    val episodes: StateFlow<Resource<List<XtreamChannelInfo.Episode>>> = series
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

    suspend fun getPlaylist(playlistUrl: String): Playlist? =
        playlistRepository.get(playlistUrl)
}
