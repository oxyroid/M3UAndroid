package com.m3u.features.feed

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.m3u.core.architecture.BaseViewModel
import com.m3u.core.wrapper.Resource
import com.m3u.core.wrapper.eventOf
import com.m3u.data.Configuration
import com.m3u.data.repository.FeedRepository
import com.m3u.data.repository.LiveRepository
import com.m3u.data.repository.observeLivesByFeedUrl
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
class FeedViewModel @Inject constructor(
    private val liveRepository: LiveRepository,
    private val feedRepository: FeedRepository,
    private val configuration: Configuration,
    application: Application
) : BaseViewModel<FeedState, FeedEvent>(
    application = application,
    emptyState = FeedState()
) {
    init {
        writable.update {
            it.copy(
                useCommonUIMode = configuration.useCommonUIMode
            )
        }
    }

    private var job: Job? = null
    override fun onEvent(event: FeedEvent) {
        when (event) {
            is FeedEvent.GetDetails -> {
                job?.cancel()
                job = viewModelScope.launch {
                    val feedUrl = event.url
                    feedRepository
                        .observe(feedUrl)
                        .onEach { feed ->
                            writable.update {
                                if (feed != null) {
                                    it.copy(
                                        url = feed.url
                                    )
                                } else {
                                    it.copy(
                                        message = eventOf(
                                            context.getString(
                                                R.string.error_get_detail,
                                                feedUrl
                                            )
                                        )
                                    )
                                }
                            }
                        }
                        .launchIn(this)
                    liveRepository
                        .observeLivesByFeedUrl(feedUrl)
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

            FeedEvent.Sync -> {
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
                feedRepository.sync(url)
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

            is FeedEvent.AddToFavourite -> {
                viewModelScope.launch {
                    val id = event.id
                    liveRepository.setFavouriteLive(id, true)
                }
            }
        }
    }
}