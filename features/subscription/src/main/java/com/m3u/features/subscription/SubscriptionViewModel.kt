package com.m3u.features.subscription

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.m3u.core.architecture.BaseViewModel
import com.m3u.core.wrapper.Resource
import com.m3u.core.wrapper.eventOf
import com.m3u.data.repository.LiveRepository
import com.m3u.data.repository.SubscriptionRepository
import com.m3u.data.repository.observeLivesBySubscriptionUrl
import com.m3u.data.repository.sync
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.MalformedURLException
import java.net.URL
import javax.inject.Inject

@HiltViewModel
class SubscriptionViewModel @Inject constructor(
    private val liveRepository: LiveRepository,
    private val subscriptionRepository: SubscriptionRepository,
    application: Application
) : BaseViewModel<SubscriptionState, SubscriptionEvent>(
    application = application,
    emptyState = SubscriptionState()
) {
    private var job: Job? = null
    override fun onEvent(event: SubscriptionEvent) {
        when (event) {
            is SubscriptionEvent.GetDetails -> {
                job?.cancel()
                job = viewModelScope.launch {
                    val subscriptionUrl = event.url
                    subscriptionRepository
                        .observe(subscriptionUrl)
                        .onEach { subscription ->
                            writable.update {
                                if (subscription != null) {
                                    it.copy(
                                        url = subscription.url
                                    )
                                } else {
                                    it.copy(
                                        message = eventOf(
                                            context.getString(
                                                R.string.error_get_detail,
                                                subscriptionUrl
                                            )
                                        )
                                    )
                                }
                            }
                        }
                        .launchIn(this)
                    liveRepository
                        .observeLivesBySubscriptionUrl(subscriptionUrl)
                        .onEach { lives ->
                            writable.update {
                                it.copy(
                                    lives = lives
                                )
                            }
                        }
                        .launchIn(this)
                }

            }

            SubscriptionEvent.Sync -> {
                val url = try {
                    URL(readable.url)
                } catch (e: MalformedURLException) {
                    writable.update {
                        it.copy(
                            syncing = false,
                            message = eventOf(e.message.orEmpty())
                        )
                    }
                    return
                }
                subscriptionRepository.sync(url)
                    .onEach { resource ->
                        writable.update {
                            when (resource) {
                                Resource.Loading -> it.copy(
                                    syncing = true
                                )

                                is Resource.Success -> it.copy(
                                    syncing = false
                                )

                                is Resource.Failure -> it.copy(
                                    syncing = false,
                                    message = eventOf(resource.message.orEmpty())
                                )
                            }
                        }
                    }
                    .launchIn(viewModelScope)
            }

            is SubscriptionEvent.AddToFavourite -> {
                viewModelScope.launch {
                    val id = event.id
                    liveRepository.setFavouriteLive(id, true)
                }
            }
        }
    }
}