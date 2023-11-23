package com.m3u.features.feed

import androidx.lifecycle.viewModelScope
import com.m3u.core.architecture.Logger
import com.m3u.core.architecture.configuration.Configuration
import com.m3u.core.architecture.viewmodel.BaseViewModel
import com.m3u.core.architecture.viewmodel.catch
import com.m3u.core.architecture.viewmodel.map
import com.m3u.core.architecture.viewmodel.onEach
import com.m3u.core.wrapper.Process
import com.m3u.core.wrapper.circuit
import com.m3u.core.wrapper.eventOf
import com.m3u.data.repository.FeedRepository
import com.m3u.data.repository.LiveRepository
import com.m3u.data.repository.MediaRepository
import com.m3u.data.repository.observeAll
import com.m3u.data.repository.refresh
import com.m3u.features.feed.FeedMessage.LiveCoverSaved
import dagger.hilt.android.lifecycle.HiltViewModel
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
    @Logger.Ui private val logger: Logger
) : BaseViewModel<FeedState, FeedEvent, FeedMessage>(
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
            val error = FeedMessage.FeedUrlNotFound
            writable.update {
                it.copy(
                    error = eventOf(error)
                )
            }
            return
        }
        observeJob = viewModelScope.launch {
            observeFeedDetail(feedUrl)
            observeFeedLives(feedUrl)
        }
    }

    private fun CoroutineScope.observeFeedDetail(feedUrl: String) {
        feedRepository
            .observe(feedUrl)
            .onEach { feed ->
                if (feed != null) {
                    writable.update {
                        it.copy(
                            url = feed.url
                        )
                    }
                } else {
                    onMessage(FeedMessage.FeedNotFound(feedUrl))
                }
            }
            .launchIn(this)
    }

    private fun CoroutineScope.observeFeedLives(feedUrl: String) {
        liveRepository
            .observeAll { !it.banned && it.feedUrl == feedUrl }
            .combine(queryStateFlow) { all, query ->
                all
                    .filter { it.title.contains(query, true) }
                    .groupBy { it.group }
                    .toList()
                    .map { Channel(it.first, it.second) }
            }
            .onEach { lives ->
                writable.update {
                    it.copy(
                        channels = lives
                    )
                }
            }
            .launchIn(this)
    }

    private fun refresh() {
        val url = readable.url
        feedRepository
            .refresh(url, readable.strategy)
            .onEach { process ->
                writable.update { prev ->
                    process
                        .circuit()
                        .catch { logger.log(it) }
                    prev.copy(
                        fetching = process is Process.Loading
                    )
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
                onMessage(FeedMessage.LiveNotFound)
                return@launch
            }
            val url = live.cover
            if (url.isNullOrEmpty()) {
                onMessage(FeedMessage.LiveCoverNotFound)
                return@launch
            }
            mediaRepository
                .savePicture(url)
                .onEach { resource ->
                    resource
                        .circuit()
                        .map { LiveCoverSaved(absolutePath) }
                        .onEach { onMessage(it) }
                        .catch { logger.log(it) }
                }
                .launchIn(this)
        }
    }

    private fun mute(event: FeedEvent.Mute) {
        viewModelScope.launch {
            val id = event.id
            val target = event.target
            val live = liveRepository.get(id)
            if (live == null) {
                onMessage(FeedMessage.LiveNotFound)
            } else {
                liveRepository.setBanned(live.id, target)
            }
        }
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