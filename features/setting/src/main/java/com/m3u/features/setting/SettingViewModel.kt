package com.m3u.features.setting

import android.app.Application
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.m3u.core.annotation.AppPublisherImpl
import com.m3u.core.annotation.ClipMode
import com.m3u.core.annotation.ConnectTimeout
import com.m3u.core.annotation.FeedStrategy
import com.m3u.core.architecture.BaseViewModel
import com.m3u.core.architecture.Publisher
import com.m3u.core.architecture.configuration.Configuration
import com.m3u.core.architecture.logger.BannerLoggerImpl
import com.m3u.core.architecture.logger.Logger
import com.m3u.core.wrapper.Resource
import com.m3u.data.repository.FeedRepository
import com.m3u.data.repository.LiveRepository
import com.m3u.data.repository.PostRepository
import com.m3u.data.repository.observeBanned
import com.m3u.data.service.JavaScriptExecutor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingViewModel @Inject constructor(
    private val feedRepository: FeedRepository,
    private val liveRepository: LiveRepository,
    @AppPublisherImpl private val publisher: Publisher,
    application: Application,
    configuration: Configuration,
    @BannerLoggerImpl private val logger: Logger,
    private val postRepository: PostRepository
) : BaseViewModel<SettingState, SettingEvent>(
    application = application,
    emptyState = SettingState(
        version = publisher.versionName,
        configuration = configuration,
        destinations = List(publisher.destinationsCount) {
            publisher.getDestination(it)
        }
    )
) {
    init {
        liveRepository.observeBanned(banned = true)
            .onEach { lives ->
                writable.update {
                    it.copy(
                        mutedLives = lives
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    override fun onEvent(event: SettingEvent) {
        when (event) {
            SettingEvent.Subscribe -> subscribe()
            is SettingEvent.OnTitle -> onTitle(event.title)
            is SettingEvent.OnUrl -> onUrl(event.url)
            SettingEvent.OnSyncMode -> onSyncMode()
            SettingEvent.OnUseCommonUIMode -> onUseCommonUIMode()
            SettingEvent.OnConnectTimeout -> onConnectTimeout()
            SettingEvent.OnExperimentalMode -> onExperimentalMode()
            SettingEvent.OnClipMode -> onClipMode()
            is SettingEvent.OnBannedLive -> onBannedLive(event.id)
            SettingEvent.OnInitialDestination -> onInitialTabIndex()
            SettingEvent.OnSilentMode -> onSilentMode()
            is SettingEvent.ImportJavaScript -> importJavaScript(event.uri)
        }
    }

    private fun importJavaScript(uri: Uri) {
        val text = context.contentResolver.openInputStream(uri)?.use { input ->
            input.bufferedReader().readText()
        }
        val result = JavaScriptExecutor.executeString(text ?: return)
        logger.log(result)
    }

    private fun onSilentMode() {
        val target = !readable.silentMode
        readable.silentMode = target
        viewModelScope.launch {
            if (target) {
                postRepository.clear()
            } else {
                postRepository.fetchAll()
            }
        }
    }

    private fun onInitialTabIndex() {
        val maxIndex = publisher.destinationsCount
        val currentIndex = readable.initialDestinationIndex
        val targetIndex = (currentIndex + 1).takeIf { it <= maxIndex } ?: 0
        readable.initialDestinationIndex = targetIndex
    }

    private fun onBannedLive(liveId: Int) {
        val bannedLive = readable.mutedLives.find { it.id == liveId }
        if (bannedLive != null) {
            viewModelScope.launch {
                liveRepository.setBanned(liveId, false)
            }
        }
    }

    private fun onClipMode() {
        val newValue = when (readable.clipMode) {
            ClipMode.ADAPTIVE -> ClipMode.CLIP
            ClipMode.CLIP -> ClipMode.STRETCHED
            ClipMode.STRETCHED -> ClipMode.ADAPTIVE
            else -> ClipMode.ADAPTIVE
        }
        readable.clipMode = newValue
    }

    private fun onExperimentalMode() {
        val newValue = !readable.experimentalMode
        if (!newValue) {
            // reset experimental ones to default value
            readable.scrollMode = Configuration.DEFAULT_SCROLL_MODE
            readable.cinemaMode = Configuration.DEFAULT_CINEMA_MODE
        }
        readable.experimentalMode = newValue
    }


    private fun onConnectTimeout() {
        val newValue = when (readable.connectTimeout) {
            ConnectTimeout.LONG -> ConnectTimeout.SHORT
            ConnectTimeout.SHORT -> ConnectTimeout.LONG
            else -> ConnectTimeout.SHORT
        }
        readable.connectTimeout = newValue
    }

    private fun onUseCommonUIMode() {
        val newValue = !readable.useCommonUIMode
        readable.useCommonUIMode = newValue
    }

    private fun onTitle(title: String) {
        writable.update {
            it.copy(
                title = Uri.decode(title)
            )
        }
    }

    private fun onUrl(url: String) {
        writable.update {
            it.copy(
                url = Uri.decode(url)
            )
        }
    }

    private fun onSyncMode() {
        val newValue = when (readable.feedStrategy) {
            FeedStrategy.ALL -> FeedStrategy.SKIP_FAVORITE
            else -> FeedStrategy.ALL
        }
        readable.feedStrategy = newValue
    }

    private fun subscribe() {
        val title = writable.value.title
        if (title.isEmpty()) {
            writable.update {
                val message = context.getString(R.string.failed_empty_title)
                logger.log(message)
                it.copy(
                    enabled = false,
                )
            }
            return
        }
        val url = readable.url
        val strategy = readable.feedStrategy
        feedRepository.subscribe(title, url, strategy)
            .onEach { resource ->
                writable.update {
                    when (resource) {
                        Resource.Loading -> {
                            it.copy(enabled = true)
                        }

                        is Resource.Success -> {
                            val message = context.getString(R.string.success_subscribe)
                            logger.log(message)
                            it.copy(
                                enabled = false,
                                title = "",
                                url = "",
                            )
                        }

                        is Resource.Failure -> {
                            val message = resource.message.orEmpty()
                            logger.log(message)
                            it.copy(
                                enabled = false
                            )
                        }
                    }
                }
            }
            .launchIn(viewModelScope)
    }

}