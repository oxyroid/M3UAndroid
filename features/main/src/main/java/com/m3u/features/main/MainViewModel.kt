package com.m3u.features.main

import androidx.lifecycle.viewModelScope
import com.m3u.core.architecture.configuration.Configuration
import com.m3u.core.architecture.viewmodel.BaseViewModel
import com.m3u.data.repository.FeedRepository
import com.m3u.data.repository.LiveRepository
import com.m3u.features.main.model.FeedDetail
import com.m3u.features.main.model.FeedDetailHolder
import com.m3u.features.main.model.toDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val feedRepository: FeedRepository,
    liveRepository: LiveRepository,
    configuration: Configuration,
) : BaseViewModel<MainState, MainEvent, MainMessage>(
    emptyState = MainState(
        configuration = configuration
    )
) {
    private val counts: StateFlow<Map<String, Int>> = liveRepository
        .observeAll()
        .map { lives ->
            lives
                .groupBy { it.feedUrl }
                .mapValues { it.value.size }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyMap()
        )

    internal val feeds: StateFlow<FeedDetailHolder> = feedRepository
        .observeAll()
        .distinctUntilChanged()
        .combine(counts) { fs, cs ->
            withContext(Dispatchers.Default) {
                fs.map { f ->
                    f.toDetail(cs[f.url] ?: FeedDetail.DEFAULT_COUNT)
                }
            }
        }
        .map { FeedDetailHolder(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = FeedDetailHolder()
        )

    override fun onEvent(event: MainEvent) {
        when (event) {
            is MainEvent.Unsubscribe -> unsubscribe(event.url)
            is MainEvent.Rename -> rename(event.feedUrl, event.target)
        }
    }

    private fun unsubscribe(url: String) {
        viewModelScope.launch {
            val feed = feedRepository.unsubscribe(url)
            if (feed == null) {
                onMessage(MainMessage.ErrorCannotUnsubscribe)
            }
        }
    }

    private fun rename(feedUrl: String, target: String) {
        viewModelScope.launch {
            feedRepository.rename(feedUrl, target)
        }
    }
}