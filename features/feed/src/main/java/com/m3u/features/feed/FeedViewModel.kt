package com.m3u.features.feed

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.m3u.core.architecture.Logger
import com.m3u.core.architecture.configuration.Configuration
import com.m3u.core.architecture.viewmodel.BaseViewModel
import com.m3u.core.wrapper.ProgressResource
import com.m3u.core.wrapper.Resource
import com.m3u.core.wrapper.eventOf
import com.m3u.data.repository.FeedRepository
import com.m3u.data.repository.LiveRepository
import com.m3u.data.repository.MediaRepository
import com.m3u.data.repository.observeAll
import com.m3u.data.repository.refresh
import com.m3u.i18n.R.string
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FeedViewModel @Inject constructor(
    private val liveRepository: LiveRepository,
    private val feedRepository: FeedRepository,
    private val mediaRepository: MediaRepository,
    configuration: Configuration,
    @Logger.Ui private val logger: Logger,
    application: Application,
) : BaseViewModel<FeedState, FeedEvent>(
    application = application,
    emptyState = FeedState(
        configuration = configuration
    )
) {
    override fun onEvent(event: FeedEvent) {
        when (event) {
            is FeedEvent.Observe -> observe(event.feedUrl)
            FeedEvent.Refresh -> refresh()
            is FeedEvent.Favourite -> favourite(event)
            FeedEvent.ScrollUp -> scrollUp()
            is FeedEvent.Mute -> mute(event)
            is FeedEvent.SavePicture -> savePicture(event)
            is FeedEvent.Query -> query(event)
        }
    }

    private var observeJob: Job? = null
    private fun observe(feedUrl: String) {
        observeJob?.cancel()
        if (feedUrl.isEmpty()) {
            val message = string(string.feat_feed_error_observe_feed, "")
            onMessage(message)
            return
        }
        observeJob = viewModelScope.launch {
            observeFeedDetail(feedUrl)
            observeFeedLives(feedUrl)
        }
    }

    private fun CoroutineScope.observeFeedDetail(feedUrl: String) {
        feedRepository.observe(feedUrl)
            .onEach { feed ->
                if (feed != null) {
                    writable.update {
                        it.copy(
                            url = feed.url
                        )
                    }
                } else {
                    val message = string(string.feat_feed_error_observe_feed, feedUrl)
                    onMessage(message)
                }
            }
            .launchIn(this)
    }

    private fun CoroutineScope.observeFeedLives(feedUrl: String) {
        liveRepository
            .observeAll { !it.banned && it.feedUrl == feedUrl }
            .combine(queryStateFlow) { origin, query ->
                val remainedLives = origin.filter {
                    it.title.contains(query, true)
                }
                remainedLives
                    .groupBy { it.group }
                    .toList()
                    .map { Channel(it.first, it.second.toPersistentList()) }
            }
            .onEach { lives ->
                writable.update {
                    it.copy(
                        channels = lives.toPersistentList()
                    )
                }
            }
            .launchIn(this)
    }

    private fun refresh() {
        val url = readable.url
        feedRepository.refresh(url, readable.strategy)
            .onEach { resource ->
                writable.update {
                    when (resource) {
                        is ProgressResource.Loading -> it.copy(
                            fetching = true
                        )

                        is ProgressResource.Success -> it.copy(
                            fetching = false
                        )

                        is ProgressResource.Failure -> {
                            val message = resource.message.orEmpty()
                            onMessage(message)
                            it.copy(
                                fetching = false
                            )
                        }
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    private fun favourite(event: FeedEvent.Favourite) {
        viewModelScope.launch {
            val id = event.id
            val target = event.target
            liveRepository.setFavourite(id, target)
        }
    }

    private fun scrollUp() {
        writable.update {
            it.copy(
                scrollUp = eventOf(Unit)
            )
        }
    }

    private fun savePicture(event: FeedEvent.SavePicture) {
        val id = event.id
        viewModelScope.launch {
            val live = liveRepository.get(id)
            if (live == null) {
                onMessage("Target live is not existed!")
            } else {
                val url = live.cover
                if (url.isNullOrEmpty()) {
                    onMessage("Target live has no cover to save")
                } else {
                    mediaRepository.savePicture(url)
                        .onEach { resource ->
                            when (resource) {
                                Resource.Loading -> {}
                                is Resource.Success -> {
                                    onMessage("Saved to ${resource.data.absolutePath}")
                                }

                                is Resource.Failure -> {
                                    onMessage(resource.message)
                                }
                            }
                        }
                        .launchIn(this)
                }
            }
        }
    }

    private fun mute(event: FeedEvent.Mute) {
        viewModelScope.launch {
            val id = event.id
            val target = event.target
            val live = liveRepository.get(id)
            if (live == null) {
                onMessage("channel is not existed!")
            } else {
                liveRepository.setBanned(live.id, target)
            }
        }
    }


    private fun onMessage(message: String?) {
        logger.log(message.orEmpty())
    }

    private val queryStateFlow = MutableStateFlow("")
    private fun query(event: FeedEvent.Query) {
        val text = event.text
        viewModelScope.launch {
            queryStateFlow.emit(text)
        }
        writable.update {
            it.copy(
                query = text
            )
        }
    }
}