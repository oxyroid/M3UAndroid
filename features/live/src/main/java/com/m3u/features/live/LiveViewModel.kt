package com.m3u.features.live

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.m3u.core.architecture.BaseViewModel
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
    application: Application
) : BaseViewModel<LiveState, LiveEvent>(
    application = application,
    emptyState = LiveState()
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