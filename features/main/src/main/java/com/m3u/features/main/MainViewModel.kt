package com.m3u.features.main

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.m3u.core.architecture.Logger
import com.m3u.core.architecture.configuration.Configuration
import com.m3u.core.architecture.viewmodel.BaseViewModel
import com.m3u.data.repository.FeedRepository
import com.m3u.data.repository.LiveRepository
import com.m3u.features.main.model.FeedDetail
import com.m3u.features.main.model.toDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.m3u.i18n.R.string

@HiltViewModel
class MainViewModel @Inject constructor(
    private val feedRepository: FeedRepository,
    liveRepository: LiveRepository,
    application: Application,
    configuration: Configuration,
    @Logger.Ui private val logger: Logger
) : BaseViewModel<MainState, MainEvent>(
    application = application,
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

    val feeds: StateFlow<List<FeedDetail>> = feedRepository
        .observeAll()
        .distinctUntilChanged()
        .combine(counts) { fs, cs ->
            fs.map { f ->
                f.toDetail(cs[f.url] ?: 0)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList()
        )

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
                val message = string(string.feat_main_error_unsubscribe_feed)
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