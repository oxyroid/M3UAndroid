package com.m3u.features.main

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.m3u.core.architecture.configuration.Configuration
import com.m3u.core.architecture.logger.UserInterfaceLogger
import com.m3u.core.architecture.viewmodel.AndroidPlatformViewModel
import com.m3u.core.util.collection.replaceIf
import com.m3u.core.util.coroutine.mapElement
import com.m3u.data.database.entity.Feed
import com.m3u.data.repository.FeedRepository
import com.m3u.data.repository.LiveRepository
import com.m3u.data.repository.observeByFeedUrl
import com.m3u.features.main.model.FeedDetail
import com.m3u.features.main.model.toDetail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(
    private val feedRepository: FeedRepository,
    private val liveRepository: LiveRepository,
    application: Application,
    configuration: Configuration,
    private val logger: UserInterfaceLogger
) : AndroidPlatformViewModel<MainState, MainEvent>(
    application = application,
    emptyState = MainState(
        configuration = configuration
    ),
) {
    init {
        var job: Job? = null
        observeAllFeeds()
            .mapElement(Feed::toDetail)
            .onEach(::setAllDetails)
            .onEach { details ->
                job?.cancel()
                job = viewModelScope.launch {
                    details.forEach { detail ->
                        val url = detail.feed.url
                        observeSize(url)
                            .onEach { count ->
                                setCountFromExistedDetails(url, count)
                            }
                            .launchIn(this)
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    private fun observeAllFeeds(): Flow<List<Feed>> = feedRepository.observeAll()

    private fun observeSize(url: String): Flow<Int> = liveRepository
        .observeByFeedUrl(url)
        .map { it.size }

    private fun setAllDetails(feeds: List<FeedDetail>) {
        writable.update {
            it.copy(
                feeds = feeds
            )
        }
    }

    private suspend fun setCountFromExistedDetails(url: String, count: Int) {
        withContext(Dispatchers.IO) {
            val predicate: (FeedDetail) -> Boolean = { it.feed.url == url }
            val transform: (FeedDetail) -> FeedDetail = { it.copy(count = count) }
            val feeds = readable.feeds.replaceIf(predicate, transform)
            setAllDetails(feeds)
        }
    }

    override fun onEvent(event: MainEvent) {
        when (event) {
            is MainEvent.UnsubscribeFeedByUrl -> unsubscribeFeedByUrl(event.url)
            is MainEvent.Rename -> rename(event.feedUrl, event.target)
        }
    }

    private fun unsubscribeFeedByUrl(url: String) {
        viewModelScope.launch {
            val feed = feedRepository.unsubscribe(url)
            if (feed == null) {
                val message = context.getString(R.string.error_unsubscribe_feed)
                logger.log(message)
            }
        }
    }

    private fun rename(feedUrl: String, target: String) {
        viewModelScope.launch {
            feedRepository.rename(feedUrl, target)
        }
    }
}