package com.m3u.features.feed

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.m3u.core.architecture.BaseViewModel
import com.m3u.core.architecture.Configuration
import com.m3u.core.wrapper.Resource
import com.m3u.core.wrapper.eventOf
import com.m3u.data.repository.FeedRepository
import com.m3u.data.repository.LiveRepository
import com.m3u.data.repository.MediaRepository
import com.m3u.data.repository.fetch
import com.m3u.data.repository.observeBannedByFeedUrl
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class FeedViewModel @Inject constructor(
    private val liveRepository: LiveRepository,
    private val feedRepository: FeedRepository,
    private val mediaRepository: MediaRepository,
    private val configuration: Configuration,
    application: Application
) : BaseViewModel<FeedState, FeedEvent>(
    application = application,
    emptyState = FeedState()
) {
    init {
        writable.update {
            it.copy(
                useCommonUIMode = configuration.useCommonUIMode,
                scrollMode = configuration.scrollMode,
                rowCount = configuration.rowCount,
                editMode = configuration.editMode
            )
        }
    }

    override fun onEvent(event: FeedEvent) {
        when (event) {
            is FeedEvent.ObserveFeed -> observeFeed(event.url)
            FeedEvent.FetchFeed -> fetchFeed()
            is FeedEvent.FavouriteLive -> favouriteFeed(event)
            FeedEvent.ScrollUp -> scrollUp()
            is FeedEvent.MuteLive -> muteLive(event)
            is FeedEvent.SavePicture -> savePicture(event)
            is FeedEvent.SetRowCount -> onRowCount(event)
            is FeedEvent.OnQuery -> onQuery(event)
        }
    }

    private var observeJob: Job? = null
    private fun observeFeed(feedUrl: String) {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            observeFeedDetail(feedUrl)
            observeFeedLives(feedUrl)
        }
    }

    private fun CoroutineScope.observeFeedDetail(feedUrl: String) {
        feedRepository.observe(feedUrl)
            .onEach { feed ->
                writable.update {
                    if (feed != null) {
                        it.copy(
                            url = feed.url,
                            title = feed.title
                        )
                    } else {
                        val message = context.getString(R.string.error_observe_feed, feedUrl)
                        it.copy(message = eventOf(message))
                    }
                }
            }
            .launchIn(this)
    }

    private fun CoroutineScope.observeFeedLives(feedUrl: String) {
        liveRepository.observeBannedByFeedUrl(
            feedUrl = feedUrl,
            banned = false
        )
            .combine(queryStateFlow) { origin, query ->
                val remainedLives = origin.filter {
                    it.title.contains(query, true)
                }
                remainedLives.groupBy { it.group }
            }
            .onEach { lives ->
                writable.update {
                    it.copy(
                        lives = lives
                    )
                }
            }
            .launchIn(this)
    }

    private fun fetchFeed() {
        val url = readable.url
        val strategy = configuration.feedStrategy
        feedRepository.fetch(url, strategy)
            .onEach { resource ->
                writable.update {
                    when (resource) {
                        Resource.Loading -> it.copy(
                            fetching = true
                        )

                        is Resource.Success -> it.copy(
                            fetching = false
                        )

                        is Resource.Failure -> it.copy(
                            fetching = false,
                            message = eventOf(resource.message.orEmpty())
                        )
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    private fun favouriteFeed(event: FeedEvent.FavouriteLive) {
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

    private fun muteLive(event: FeedEvent.MuteLive) {
        viewModelScope.launch {
            val id = event.id
            val target = event.target
            val live = liveRepository.get(id)
            if (live == null) {
                writable.update {
                    it.copy(
                        message = eventOf("Live is not existed!")
                    )
                }
            } else {
                liveRepository.setBanned(live.id, target)
            }
        }
    }

    private fun onRowCount(event: FeedEvent.SetRowCount) {
        configuration.rowCount = event.count
        writable.update {
            it.copy(
                rowCount = event.count
            )
        }
    }

    private fun onMessage(message: String?) {
        writable.update {
            it.copy(
                message = eventOf(message.orEmpty())
            )
        }
    }

    private val queryStateFlow = MutableStateFlow("")
    private fun onQuery(event: FeedEvent.OnQuery) {
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