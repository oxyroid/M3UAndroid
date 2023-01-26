package com.m3u.features.live

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.m3u.core.BaseViewModel
import com.m3u.core.util.createClazzKey
import com.m3u.data.repository.LiveRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class LiveViewModel @Inject constructor(
    private val liveRepository: LiveRepository,
    savedStateHandle: SavedStateHandle
) : BaseViewModel<LiveState, LiveEvent>(
    emptyState = LiveState(),
    savedStateHandle = savedStateHandle,
    key = createClazzKey<LiveViewModel>()
) {
    override fun onEvent(event: LiveEvent) {
        when (event) {
            is LiveEvent.Init -> init(event.liveId)
        }
    }

    private var initJob: Job? = null
    private fun init(id: Int) {
        initJob?.cancel()
        initJob = liveRepository.observe(id)
            .onEach { live ->
                writable.update {
                    it.copy(
                        live = live
                    )
                }
            }
            .launchIn(viewModelScope)
    }
}