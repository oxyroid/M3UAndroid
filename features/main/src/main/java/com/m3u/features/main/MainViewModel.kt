package com.m3u.features.main

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.m3u.core.BaseViewModel
import com.m3u.core.collection.replaceIf
import com.m3u.core.util.createClazzKey
import com.m3u.data.repository.LiveRepository
import com.m3u.data.repository.SubscriptionRepository
import com.m3u.features.main.vo.SubscriptionDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

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
    init {
        viewModelScope.launch {
            subscriptionRepository.observeAllSubscriptions()
//            .map { subscriptions ->
//                subscriptions
//                    .asFlow()
//                    .flatMapMerge { subscription ->
//                        liveRepository
//                            .observeLivesBySubscriptionUrl(subscription.url)
//                            .map { SubscriptionDetail(subscription, it.size) }
//                    }
//                    .distinctUntilChanged()
//                    .toList()
//            }
                .map { subscriptions ->
                    subscriptions.map {
                        SubscriptionDetail(it, 0)
                    }
                }
                .onEach { details ->
                    details
                        .asFlow()
                        .collectLatest { detail ->
                            val url = detail.subscription.url
                            liveRepository
                                .observeLivesBySubscriptionUrl(url)
                                .map { it.size }
                                .onEach { count ->
                                    updateCountByUrl(url, count)
                                }
                                .launchIn(this)
                        }
                }
                .collectLatest(::deliverSubscriptionDetails)
        }
    }

    private fun deliverSubscriptionDetails(subscriptions: List<SubscriptionDetail>) {
        writable.update {
            it.copy(
                subscriptions = subscriptions
            )
        }
    }

    private suspend fun updateCountByUrl(url: String, count: Int) {
        withContext(Dispatchers.IO) {
            val subscriptions = readable.value.subscriptions
                .replaceIf(
                    predicate = {
                        it.subscription.url == url
                    },
                    transform = {
                        it.copy(
                            count = count
                        )
                    }
                )
            deliverSubscriptionDetails(subscriptions)
        }
    }

    override fun onEvent(event: MainEvent) {

    }
}