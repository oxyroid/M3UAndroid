package com.m3u.features.main

import androidx.lifecycle.viewModelScope
import com.m3u.core.BaseViewModel
import com.m3u.data.repository.SubscriptionRepository
import com.m3u.features.main.vo.toViewObject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    subscriptionRepository: SubscriptionRepository
) : BaseViewModel<MainState, MainEvent>(MainState.Loading) {
    init {
        subscriptionRepository.observeAllSubscriptions()
            .onEach { subscriptions ->
                writable.update {
                    val vos = subscriptions.map { it.toViewObject() }
                    MainState.Success(
                        subscriptions = vos
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    override fun onEvent(event: MainEvent) {

    }
}