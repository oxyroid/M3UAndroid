package com.m3u.features.setting

import androidx.lifecycle.viewModelScope
import com.m3u.core.BaseViewModel
import com.m3u.core.BuildConfigProvider
import com.m3u.core.wrapper.Resource
import com.m3u.core.wrapper.eventOf
import com.m3u.data.repository.SubscriptionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import java.net.MalformedURLException
import java.net.URL
import javax.inject.Inject

@HiltViewModel
class SettingViewModel @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository,
    buildConfigProvider: BuildConfigProvider,
) : BaseViewModel<SettingState, SettingEvent>(SettingState()) {
    init {
        writable.update {
            it.copy(
                appVersion = buildConfigProvider.version()
            )
        }
    }

    override fun onEvent(event: SettingEvent) {
        when (event) {
            is SettingEvent.OnUrlSubmit -> {
                val url = try {
                    URL(event.url)
                } catch (e: MalformedURLException) {
                    writable.update {
                        it.copy(
                            adding = false,
                            message = eventOf(e.message.orEmpty())
                        )
                    }
                    return
                }
                subscriptionRepository.parseUrlToLocal(url)
                    .onEach { resource ->
                        writable.update {
                            when (resource) {
                                Resource.Loading -> {
                                    it.copy(
                                        adding = true
                                    )
                                }
                                is Resource.Success -> {
                                    it.copy(
                                        adding = false
                                    )
                                }
                                is Resource.Failure -> {
                                    it.copy(
                                        adding = false,
                                        message = eventOf(resource.message.orEmpty())
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