package com.m3u.features.setting

import androidx.lifecycle.viewModelScope
import com.m3u.core.BaseViewModel
import com.m3u.core.wrapper.Resource
import com.m3u.data.repository.SubscriptionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import java.net.URL
import javax.inject.Inject

@HiltViewModel
class SettingViewModel @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository
) : BaseViewModel<SettingState, SettingEvent>(SettingState()) {
    override fun onEvent(event: SettingEvent) {
        when (event) {
            is SettingEvent.OnUrlSubmit -> {
                val url = URL(event.url)
                subscriptionRepository.parseUrlToLocal(url)
                    .onEach { resource ->
                        writable.update {
                            when (resource) {
                                Resource.Loading -> it.copy(
                                    adding = true
                                )
                                is Resource.Success -> it.copy(
                                    adding = false
                                )
                                is Resource.Failure -> {
                                    it.copy(
                                        adding = false
                                    )
                                }
                            }
                        }
                    }
                    .launchIn(viewModelScope)
            }
        }
    }
}