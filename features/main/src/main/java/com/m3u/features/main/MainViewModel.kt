package com.m3u.features.main

import android.app.Application
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.m3u.core.BaseViewModel
import com.m3u.core.util.createClazzKey
import com.m3u.data.repository.LiveRepository
import com.m3u.data.repository.SubscriptionRepository
import com.m3u.features.main.vo.SubscriptionDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class MainViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    subscriptionRepository: SubscriptionRepository,
    liveRepository: LiveRepository,
    application: Application
) : BaseViewModel<MainState, MainEvent>(
    application = application,
    emptyState = MainState(),
    savedStateHandle = savedStateHandle,
    key = createClazzKey<MainViewModel>()
) {
    private val TAG = "MainViewModel"
    private var allSubscriptionsJob: Job? = null

    init {
        allSubscriptionsJob?.cancel()
        subscriptionRepository.observeAllSubscriptions()
            .map { subscriptions ->
                subscriptions
                    .asFlow()
                    .flatMapMerge { subscription ->
                        liveRepository
                            .observeLivesBySubscriptionUrl(subscription.url)
                            .map { SubscriptionDetail(subscription, it.size) }
                    }
                    .onEach {
                        Log.d(TAG, "before: $it")
                    }
                    .toList()
                    .also {
                        Log.d(TAG, "after: $it")
                    }
            }
            .onEach(::deliverSubscriptionDetails)
            .launchIn(viewModelScope)
    }

    private fun deliverSubscriptionDetails(subscriptions: List<SubscriptionDetail>) {
        writable.update {
            it.copy(
                subscriptions = subscriptions
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        allSubscriptionsJob?.cancel()
    }

    override fun onEvent(event: MainEvent) {

    }
}