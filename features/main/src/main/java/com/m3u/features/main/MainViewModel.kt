package com.m3u.features.main

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.m3u.core.architecture.BaseViewModel
import com.m3u.core.util.collection.replaceIf
import com.m3u.data.entity.Feed
import com.m3u.data.repository.FeedRepository
import com.m3u.data.repository.LiveRepository
import com.m3u.data.repository.observeLivesByFeedUrl
import com.m3u.features.main.model.FeedDetail
import com.m3u.features.main.model.toDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val feedRepository: FeedRepository,
    private val liveRepository: LiveRepository,
    application: Application
) : BaseViewModel<MainState, MainEvent>(
    application = application,
    emptyState = MainState()
) {
    init {
        var job: Job? = null
        observeAllFeeds()
            .map { it.map(Feed::toDetail) }
            .onEach(::setAllDetails)
            .onEach { details ->
                job?.cancel()
                job = viewModelScope.launch {
                    details.forEach { detail ->
                        val url = detail.feed.url
                        observeSize(url)
                            .onEach { count -> setCountFromExistedDetails(url, count) }
                            .launchIn(this)
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    private fun observeAllFeeds(): Flow<List<Feed>> = feedRepository.observeAll()

    private fun observeSize(url: String): Flow<Int> = liveRepository
        .observeLivesByFeedUrl(url)
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

    }
}