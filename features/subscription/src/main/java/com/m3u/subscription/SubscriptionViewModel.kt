package com.m3u.subscription

import androidx.lifecycle.viewModelScope
import com.m3u.core.BaseViewModel
import com.m3u.data.repository.LiveRepository
import com.m3u.data.repository.SubscriptionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class SubscriptionViewModel @Inject constructor(
    private val liveRepository: LiveRepository,
    private val subscriptionRepository: SubscriptionRepository
) : BaseViewModel<SubscriptionState, SubscriptionEvent>(SubscriptionState()) {
    private var detailJob: Job? = null
    private var livesJob: Job? = null
    override fun onEvent(event: SubscriptionEvent) {
        when (event) {
            is SubscriptionEvent.GetDetails -> {
                detailJob?.cancel()
                livesJob?.cancel()
                val subscriptionId = event.id
                detailJob = subscriptionRepository.observeDetail(subscriptionId)
                    .onEach { subscription ->
                        writable.update {
                            it.copy(
                                title = subscription?.title.orEmpty()
                            )
                        }
                    }
                    .launchIn(viewModelScope)
                livesJob = liveRepository.observeBySubscriptionId(subscriptionId)
                    .onEach { lives ->
                        writable.update {
                            it.copy(
                                lives = lives
                            )
                        }
                    }
                    .launchIn(viewModelScope)
            }
        }
    }
}