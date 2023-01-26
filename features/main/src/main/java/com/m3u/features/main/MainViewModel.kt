package com.m3u.features.main

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.m3u.core.BaseViewModel
import com.m3u.core.util.createClazzKey
import com.m3u.data.repository.LiveRepository
import com.m3u.data.repository.SubscriptionRepository
import com.m3u.features.main.vo.SubscriptionDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    subscriptionRepository: SubscriptionRepository,
    liveRepository: LiveRepository
) : BaseViewModel<MainState, MainEvent>(
    emptyState = MainState(),
    savedStateHandle = savedStateHandle,
    key = createClazzKey<MainViewModel>()
) {
    private var job: Job? = null

    init {
        job?.cancel()
        job = subscriptionRepository.observeAllSubscriptions()
            .map { subscriptions ->
                subscriptions.map {
                    SubscriptionDetail(it, liveRepository.getBySubscriptionUrl(it.url).count())
                }
            }
            .distinctUntilChanged()
            .onEach { subscriptions ->
                writable.update {
                    it.copy(
                        subscriptions = subscriptions
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    override fun onCleared() {
        super.onCleared()
        job?.cancel()
    }

    override fun onEvent(event: MainEvent) {

    }
}