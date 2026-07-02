package com.m3u.tv

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import com.m3u.core.architecture.preferences.PreferencesKeys
import com.m3u.core.architecture.preferences.Settings
import com.m3u.core.architecture.preferences.get
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.DataSource
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.isSeries
import com.m3u.data.repository.channel.ChannelRepository
import com.m3u.data.repository.playlist.PlaylistRepository
import com.m3u.data.repository.tv.TvRepository
import com.m3u.data.service.DPadReactionService
import com.m3u.data.service.MediaCommand
import com.m3u.data.service.PlayerManager
import com.m3u.data.parser.xtream.XtreamInput
import com.m3u.data.worker.SubscriptionWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class TvUiState(
    val playlists: List<Playlist> = emptyList(),
    val counts: Map<Playlist, Int> = emptyMap(),
    val selectedPlaylist: Playlist? = null,
    val channels: List<Channel> = emptyList(),
    val favorites: List<Channel> = emptyList(),
    val recent: Channel? = null,
    val loadingChannels: Boolean = false
) {
    val channelCount: Int get() = counts.values.sum()
    val heroChannel: Channel? get() = recent ?: channels.firstOrNull()
}

enum class TvXtreamSubscriptionMessage {
    MissingFields,
    InvalidUrl,
    Enqueued
}

@HiltViewModel
class TvHomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playlistRepository: PlaylistRepository,
    private val channelRepository: ChannelRepository,
    private val playerManager: PlayerManager,
    private val settings: Settings,
    private val workManager: WorkManager,
    tvRepository: TvRepository,
    dPadReactionService: DPadReactionService
) : ViewModel() {
    private val _state = MutableStateFlow(TvUiState())
    val state: StateFlow<TvUiState> = _state.asStateFlow()
    private val _xtreamSubscriptionMessage = MutableStateFlow<TvXtreamSubscriptionMessage?>(null)
    val xtreamSubscriptionMessage: StateFlow<TvXtreamSubscriptionMessage?> =
        _xtreamSubscriptionMessage.asStateFlow()

    val player: StateFlow<Player?> = playerManager.player
    val currentChannel: StateFlow<Channel?> = playerManager.channel
    val currentFavorite: StateFlow<Boolean> = combine(currentChannel, state) { channel, state ->
        channel != null && state.favorites.any { it.id == channel.id }
    }
        .stateIn(
            scope = viewModelScope,
            initialValue = false,
            started = SharingStarted.WhileSubscribed(5_000)
        )
    val isPlaying: StateFlow<Boolean> = playerManager.isPlaying
    val playbackState: StateFlow<Int> = playerManager.playbackState
    val remoteControlCode: StateFlow<Int?> = tvRepository.broadcastCodeOnTv
    val remoteDirections = dPadReactionService.incoming
    val subscribingXtream: StateFlow<Boolean> = workManager
        .getWorkInfosFlow(
            WorkQuery.fromStates(
                WorkInfo.State.RUNNING,
                WorkInfo.State.ENQUEUED
            )
        )
        .mapLatest { infos ->
            infos.any { info -> DataSource.Xtream.value in info.tags }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            initialValue = false,
            started = SharingStarted.WhileSubscribed(5_000)
        )
    private val _startupPlaybackRequests = MutableSharedFlow<Unit>()
    val startupPlaybackRequests: SharedFlow<Unit> = _startupPlaybackRequests
    private var loadChannelsJob: Job? = null

    init {
        observePlaylists()
        observeFavorites()
        observeRecent()
        resumeLastChannelOnStartup()
    }

    fun selectPlaylist(playlist: Playlist) {
        if (_state.value.selectedPlaylist?.url == playlist.url) return
        _state.update {
            it.copy(
                selectedPlaylist = playlist,
                channels = emptyList(),
                loadingChannels = true
            )
        }
        loadChannels(playlist.url)
    }

    fun refreshSelectedPlaylist() {
        val playlist = state.value.selectedPlaylist ?: return
        viewModelScope.launch {
            playlistRepository.refresh(playlist.url)
            loadChannels(playlist.url)
        }
    }

    fun addXtreamPlaylist(
        title: String,
        basicUrl: String,
        username: String,
        password: String,
        type: String?
    ) {
        val normalizedTitle = title.trim()
        val normalizedBasicUrl = basicUrl.trim().let { input ->
            if (input.startsWith("http://", ignoreCase = true) ||
                input.startsWith("https://", ignoreCase = true)
            ) {
                input
            } else {
                "http://$input"
            }
        }
        val normalizedUsername = username.trim()
        val normalizedPassword = password.trim()
        if (
            normalizedTitle.isBlank() ||
            normalizedBasicUrl.removePrefix("http://").removePrefix("https://").isBlank() ||
            normalizedUsername.isBlank() ||
            normalizedPassword.isBlank()
        ) {
            _xtreamSubscriptionMessage.value = TvXtreamSubscriptionMessage.MissingFields
            return
        }

        val url = runCatching {
            XtreamInput.encodeToPlaylistUrl(
                XtreamInput(
                    basicUrl = normalizedBasicUrl,
                    username = normalizedUsername,
                    password = normalizedPassword,
                    type = type
                )
            )
        }.getOrElse {
            _xtreamSubscriptionMessage.value = TvXtreamSubscriptionMessage.InvalidUrl
            return
        }

        SubscriptionWorker.xtream(
            workManager = workManager,
            title = normalizedTitle,
            url = url,
            basicUrl = normalizedBasicUrl,
            username = normalizedUsername,
            password = normalizedPassword
        )
        _xtreamSubscriptionMessage.value = TvXtreamSubscriptionMessage.Enqueued
    }

    fun clearXtreamSubscriptionMessage() {
        _xtreamSubscriptionMessage.value = null
    }

    fun play(channel: Channel) {
        viewModelScope.launch {
            playerManager.play(MediaCommand.Common(channel.id))
            channelRepository.reportPlayed(channel.id)
        }
    }

    fun playRecent() {
        state.value.recent?.let(::play)
    }

    fun toggleFavorite(channel: Channel) {
        viewModelScope.launch {
            channelRepository.favouriteOrUnfavourite(channel.id)
        }
    }

    fun toggleCurrentFavorite() {
        currentChannel.value?.let(::toggleFavorite)
    }

    fun pauseOrContinue(continuePlayback: Boolean) {
        playerManager.pauseOrContinue(continuePlayback)
    }

    fun playPreviousChannel() {
        playAdjacentChannel(step = -1)
    }

    fun playNextChannel() {
        playAdjacentChannel(step = 1)
    }

    fun releasePlayer() {
        playerManager.release()
    }

    private fun playAdjacentChannel(step: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = currentChannel.value ?: return@launch
            val channels = channelsForPlayback(current.playlistUrl)
            val currentIndex = channels.indexOfFirst { it.id == current.id }
            val target = channels.getOrNull(currentIndex + step) ?: return@launch
            play(target)
        }
    }

    private fun observePlaylists() {
        viewModelScope.launch {
            playlistRepository
                .observeAllCounts()
                .flowOn(Dispatchers.Default)
                .collect { counts ->
                    val state = _state.value
                    val playlists = counts.keys
                        .filterNot { it.source == DataSource.EPG }
                        .sortedBy { it.title.lowercase() }
                    val previous = state.selectedPlaylist
                    val selected = previous
                        ?.let { active -> playlists.firstOrNull { it.url == active.url } }
                        ?: playlists.firstOrNull()
                    val previousCount = previous?.let { playlist -> state.counts.countFor(playlist.url) }
                    val selectedCount = selected?.let { playlist -> counts.countFor(playlist.url) }

                    _state.update {
                        it.copy(
                            playlists = playlists,
                            counts = counts,
                            selectedPlaylist = selected
                        )
                    }

                    if (selected != null && (selected.url != previous?.url || selectedCount != previousCount)) {
                        loadChannels(selected.url)
                    }
                }
        }
    }

    private fun observeFavorites() {
        viewModelScope.launch {
            channelRepository.observeAllFavorite().collect { favorites ->
                _state.update { it.copy(favorites = favorites) }
            }
        }
    }

    private fun observeRecent() {
        viewModelScope.launch {
            channelRepository.observePlayedRecently().collect { recent ->
                _state.update { it.copy(recent = recent) }
            }
        }
    }

    private fun resumeLastChannelOnStartup() {
        viewModelScope.launch {
            if (!settings[PreferencesKeys.RESUME_LAST_CHANNEL_ON_STARTUP]) {
                return@launch
            }
            val startupDelay = settings[PreferencesKeys.STARTUP_DELAY]
            if (startupDelay > 0) {
                delay(startupDelay)
            }
            if (!isNetworkConnected()) {
                return@launch
            }
            val channel = channelRepository.getPlayedRecently() ?: return@launch
            val playlist = playlistRepository.get(channel.playlistUrl)
            if (playlist?.isSeries == true) {
                return@launch
            }
            play(channel)
            _startupPlaybackRequests.emit(Unit)
        }
    }

    private fun isNetworkConnected(): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun loadChannels(url: String) {
        loadChannelsJob?.cancel()
        loadChannelsJob = viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(loadingChannels = true) }
            val channels = channelsForPlayback(url)
            _state.update { state ->
                if (state.selectedPlaylist?.url == url) {
                    state.copy(
                        channels = channels,
                        loadingChannels = false
                    )
                } else {
                    state
                }
            }
        }
    }

    private suspend fun channelsForPlayback(playlistUrl: String): List<Channel> =
        channelRepository
            .getByPlaylistUrl(playlistUrl)
            .filterNot { it.hidden }
            .sortedWith(
                compareBy<Channel> { it.category.lowercase() }
                    .thenBy { it.title.lowercase() }
            )

    private fun Map<Playlist, Int>.countFor(url: String): Int? =
        entries.firstOrNull { it.key.url == url }?.value
}
