package com.m3u.features.live

import com.m3u.core.BaseViewModel
import com.m3u.data.entity.Live
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class LiveViewModel @Inject constructor(

) : BaseViewModel<LiveState, LiveEvent>(LiveState.Loading()) {
    override fun onEvent(event: LiveEvent) {
        when (event) {
            is LiveEvent.Init -> init(event.live)
        }
    }

    private fun init(live: Live) {
        writable.update { pre ->
            when (pre) {
                is LiveState.Loading -> {
                    pre.copy(
                        live = live
                    )
                }
                is LiveState.Result -> {
                    LiveState.Loading(
                        live = live
                    )
                }
            }
        }
    }
}