package com.m3u.features.live

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.m3u.core.architecture.BaseViewModel
import com.m3u.core.architecture.configuration.Configuration
import com.m3u.core.wrapper.eventOf
import com.m3u.data.repository.LiveRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class LiveViewModel @Inject constructor(
    private val liveRepository: LiveRepository,
    application: Application,
    configuration: Configuration
) : BaseViewModel<LiveState, LiveEvent>(
    application = application,
    emptyState = LiveState()
) {
    init {
        writable.update {
            it.copy(
                experimentalMode = configuration.experimentalMode,
                clipMode = configuration.clipMode
            )
        }
    }

    override fun onEvent(event: LiveEvent) {
        when (event) {
            is LiveEvent.Init.SingleLive -> initLive(event.liveId)
            is LiveEvent.Init.PlayList -> initPlaylist(event.ids, event.initialIndex)
            LiveEvent.SearchDlnaDevices -> searchDlnaDevices()
            LiveEvent.Record -> record()
        }
    }

    private var initJob: Job? = null
    private fun initLive(id: Int) {
        initJob?.cancel()
        initJob = liveRepository.observe(id)
            .onEach { live ->
                writable.update {
                    it.copy(
                        init = LiveState.Init.Live(live)
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    private fun initPlaylist(ids: List<Int>, initialIndex: Int) {
        initJob?.cancel()
        initJob = viewModelScope.launch {
            val lives = when (val init = readable.init) {
                is LiveState.Init.PlayList -> init.lives
                is LiveState.Init.Live -> init.live?.let(::listOf) ?: emptyList()
            }.toMutableList()
            ids.forEach { id ->
                val live = liveRepository.get(id)
                if (live != null) {
                    lives.add(live)
                }
            }
            writable.update { readable ->
                readable.copy(
                    init = LiveState.Init.PlayList(
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
}