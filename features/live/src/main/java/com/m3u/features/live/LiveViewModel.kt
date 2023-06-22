package com.m3u.features.live

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.m3u.core.architecture.viewmodel.BaseViewModel
import com.m3u.core.architecture.configuration.Configuration
import com.m3u.core.architecture.service.PlayerManager
import com.m3u.core.wrapper.eventOf
import com.m3u.data.repository.FeedRepository
import com.m3u.data.repository.LiveRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LiveViewModel(
    private val liveRepository: LiveRepository,
    private val feedRepository: FeedRepository,
    application: Application,
    configuration: Configuration,
    private val playerManager: PlayerManager,
) : BaseViewModel<LiveState, LiveEvent>(
    application = application,
    emptyState = LiveState(
        configuration = configuration
    )
) {
    init {
        playerManager.observePlayer()
            .onEach { player ->
                writable.update {
                    it.copy(
                        player = player,
                        muted = player?.volume == 0f
                    )
                }
            }
            .launchIn(viewModelScope)

        playerManager.initPlayer()

        combine(
            playerManager.playbackState,
            playerManager.videoSize,
            playerManager.playerError
        ) { playback, videoSize, playerError ->
            LiveState.PlayerState(
                playback = playback,
                videoSize = videoSize,
                playerError = playerError
            )
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = LiveState.PlayerState()
            )
            .onEach { playerState ->
                writable.value = readable.copy(
                    playerState = playerState
                )
            }
            .launchIn(viewModelScope)
    }

    override fun onEvent(event: LiveEvent) {
        when (event) {
            is LiveEvent.InitSpecial -> initSpecial(event.liveId)
            is LiveEvent.InitPlayList -> initPlaylist(event.ids, event.initialIndex)
            LiveEvent.SearchDlnaDevices -> searchDlnaDevices()
            LiveEvent.Record -> record()
            is LiveEvent.InstallMedia -> {
                val url = event.url
                if (url.isEmpty()) return
                playerManager.installMedia(url)
            }

            LiveEvent.UninstallMedia -> {
                playerManager.uninstallMedia()
            }

            LiveEvent.OnMuted -> muted()
        }
    }

    private var initJob: Job? = null
    private fun initSpecial(id: Int) {
        initJob?.cancel()
        initJob = viewModelScope.launch {
            liveRepository.observe(id)
                .onEach { live ->
                    if (live != null) {
                        val feed = feedRepository.get(live.feedUrl)
                        writable.update {
                            it.copy(
                                init = LiveState.InitSpecial(
                                    live = live,
                                    feed = feed
                                )
                            )
                        }
                    }
                }
                .launchIn(this)
        }
    }

    private fun initPlaylist(ids: List<Int>, initialIndex: Int) {
        initJob?.cancel()
        initJob = viewModelScope.launch {
            val lives = when (val init = readable.init) {
                is LiveState.InitPlayList -> init.lives
                is LiveState.InitSpecial -> init.live?.let(::listOf) ?: emptyList()
            }.toMutableList()
            ids.forEach { id ->
                val live = liveRepository.get(id)
                if (live != null) {
                    lives.add(live)
                }
            }
            writable.update { readable ->
                readable.copy(
                    init = LiveState.InitPlayList(
                        lives = lives,
                        initialIndex = initialIndex
                    ),
                )
            }
        }
    }

    private fun searchDlnaDevices() {
        writable.update {
            it.copy(
                message = eventOf("Working in progress!")
            )
        }
    }

    private fun record() {
        writable.update {
            it.copy(
                recording = !readable.recording
            )
        }
    }

    private fun muted() {
        val target = !readable.muted
        val volume = if (target) 0f else 1f
        readable.player?.volume = volume
        writable.update {
            it.copy(
                muted = target
            )
        }
    }
}