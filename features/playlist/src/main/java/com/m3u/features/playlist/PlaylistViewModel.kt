package com.m3u.features.playlist

import androidx.lifecycle.viewModelScope
import com.m3u.core.architecture.Logger
import com.m3u.core.architecture.pref.Pref
import com.m3u.core.architecture.pref.observeAsFlow
import com.m3u.core.architecture.viewmodel.BaseViewModel
import com.m3u.core.architecture.viewmodel.catch
import com.m3u.core.architecture.viewmodel.map
import com.m3u.core.architecture.viewmodel.onEach
import com.m3u.core.wrapper.Process
import com.m3u.core.wrapper.circuit
import com.m3u.core.wrapper.eventOf
import com.m3u.data.repository.PlaylistRepository
import com.m3u.data.repository.StreamRepository
import com.m3u.data.repository.MediaRepository
import com.m3u.data.repository.observeAll
import com.m3u.data.repository.refresh
import com.m3u.data.service.PlayerManager
import com.m3u.features.playlist.PlaylistMessage.StreamCoverSaved
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val streamRepository: StreamRepository,
    private val playlistRepository: PlaylistRepository,
    private val mediaRepository: MediaRepository,
    playerManager: PlayerManager,
    private val pref: Pref,
    @Logger.Ui private val logger: Logger
) : BaseViewModel<PlaylistState, PlaylistEvent, PlaylistMessage>(
    emptyState = PlaylistState()
) {
    override fun onEvent(event: PlaylistEvent) {
        when (event) {
            is PlaylistEvent.Observe -> observe(event.playlistUrl)
            PlaylistEvent.Refresh -> refresh()
            is PlaylistEvent.Favourite -> favourite(event)
            PlaylistEvent.ScrollUp -> scrollUp()
            is PlaylistEvent.Mute -> mute(event)
            is PlaylistEvent.SavePicture -> savePicture(event)
            is PlaylistEvent.Query -> query(event)
        }
    }

    private val zappingMode = pref
        .observeAsFlow { it.zappingMode }
        .stateIn(
            scope = viewModelScope,
            initialValue = Pref.DEFAULT_ZAPPING_MODE,
            started = SharingStarted.WhileSubscribed(5_000)
        )

    val floating = combine(
        zappingMode,
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

    private var observeJob: Job? = null
    private fun observe(playlistUrl: String) {
        observeJob?.cancel()
        if (playlistUrl.isEmpty()) {
            val error = PlaylistMessage.PlaylistUrlNotFound
            writable.update {
                it.copy(
                    error = eventOf(error)
                )
            }
            return
        }
        observeJob = viewModelScope.launch {
            observePlaylistDetail(playlistUrl)
            observestreams(playlistUrl)
        }
    }

    private fun CoroutineScope.observePlaylistDetail(playlistUrl: String) {
        playlistRepository
            .observe(playlistUrl)
            .onEach { playlist ->
                if (playlist != null) {
                    writable.update {
                        it.copy(
                            url = playlist.url
                        )
                    }
                } else {
                    onMessage(PlaylistMessage.PlaylistNotFound(playlistUrl))
                }
            }
            .launchIn(this)
    }

    private fun CoroutineScope.observestreams(playlistUrl: String) {
        streamRepository
            .observeAll { !it.banned && it.playlistUrl == playlistUrl }
            .combine(queryStateFlow) { all, query ->
                all
                    .filter { it.title.contains(query, true) }
                    .groupBy { it.group }
                    .toList()
                    .map { Channel(it.first, it.second) }
            }
            .onEach { streams ->
                writable.update {
                    it.copy(
                        channels = streams
                    )
                }
            }
            .launchIn(this)
    }

    private fun refresh() {
        val url = readable.url
        playlistRepository
            .refresh(url, pref.playlistStrategy)
            .onEach { process ->
                writable.update { prev ->
                    process
                        .circuit()
                        .catch { logger.log(it) }
                    prev.copy(
                        fetching = process is Process.Loading
                    )
                }
            }
            .launchIn(viewModelScope)
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
                onMessage(PlaylistMessage.StreamNotFound)
                return@launch
            }
            val url = stream.cover
            if (url.isNullOrEmpty()) {
                onMessage(PlaylistMessage.StreamCoverNotFound)
                return@launch
            }
            mediaRepository
                .savePicture(url)
                .onEach { resource ->
                    resource
                        .circuit()
                        .map { StreamCoverSaved(absolutePath) }
                        .onEach { onMessage(it) }
                        .catch { logger.log(it) }
                }
                .launchIn(this)
        }
    }

    private fun mute(event: PlaylistEvent.Mute) {
        viewModelScope.launch {
            val id = event.id
            val target = event.target
            val stream = streamRepository.get(id)
            if (stream == null) {
                onMessage(PlaylistMessage.StreamNotFound)
            } else {
                streamRepository.setBanned(stream.id, target)
            }
        }
    }

    private val queryStateFlow = MutableStateFlow("")
    private fun query(event: PlaylistEvent.Query) {
        val text = event.text
        viewModelScope.launch {
            queryStateFlow.emit(text)
        }
        writable.update {
            it.copy(
                query = text
            )
        }
    }
}