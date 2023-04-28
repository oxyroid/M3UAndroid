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
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SettingViewModel @Inject constructor(
    private val feedRepository: FeedRepository,
    private val liveRepository: LiveRepository,
    @AppPublisherImpl private val publisher: Publisher,
    application: Application,
    private val configuration: Configuration,
    @BannerLoggerImpl private val logger: Logger,
    private val postRepository: PostRepository
) : BaseViewModel<SettingState, SettingEvent>(
    application = application,
    emptyState = SettingState()
) {
    init {
        writable.update {
            it.copy(
                feedStrategy = configuration.feedStrategy,
                useCommonUIMode = configuration.useCommonUIMode,
                experimentalMode = configuration.experimentalMode,
                godMode = configuration.godMode,
                connectTimeout = configuration.connectTimeout,
                clipMode = configuration.clipMode,
                scrollMode = configuration.scrollMode,
                autoRefresh = configuration.autoRefresh,
                isSSLVerificationEnabled = configuration.isSSLVerification,
                fullInfoPlayer = configuration.fullInfoPlayer,
                initialTabTitle = configuration.initialTabIndex,
                tabTitles = createTabTitles(publisher.maxTabIndex),
                isNeverDeliverCover = configuration.isNeverDeliverCover,
                silentMode = configuration.silentMode
            )
        }
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
            SettingEvent.OnSubscribe -> subscribe()
            is SettingEvent.OnTitle -> onTitle(event.title)
            is SettingEvent.OnUrl -> onUrl(event.url)
            SettingEvent.OnSyncMode -> onSyncMode()
            SettingEvent.OnUseCommonUIMode -> onUseCommonUIMode()
            SettingEvent.OnConnectTimeout -> onConnectTimeout()
            SettingEvent.OnGodMode -> onGodMode()
            SettingEvent.OnExperimentalMode -> onExperimentalMode()
            SettingEvent.OnClipMode -> onClipMode()
            is SettingEvent.OnBannedLive -> onBannedLive(event.id)
            SettingEvent.OnScrollMode -> onScrollMode()
            SettingEvent.OnAutoRefresh -> onAutoRefresh()
            SettingEvent.OnSSLVerificationEnabled -> onSSLVerificationEnabled()
            SettingEvent.OnFullInfoPlayer -> onFullInfoPlayer()
            SettingEvent.OnInitialTabIndex -> onInitialTabIndex()
            SettingEvent.OnNeverDeliverCover -> onNeverDeliverCover()
            SettingEvent.OnSilentMode -> onSilentMode()
        }
    }

    private fun onSilentMode() {
        val target = !configuration.silentMode
        configuration.silentMode = target
        viewModelScope.launch {
            if (target) {
                postRepository.clear()
            } else {
                postRepository.fetchAll()
            }
        }
        writable.update {
            it.copy(
                silentMode = target
            )
        }
    }

    private fun onNeverDeliverCover() {
        val target = !configuration.isNeverDeliverCover
        configuration.isNeverDeliverCover = target
        writable.update {
            it.copy(
                isNeverDeliverCover = target
            )
        }
    }

    private fun onInitialTabIndex() {
        val maxIndex = publisher.maxTabIndex
        val currentIndex = readable.initialTabTitle
        val targetIndex = (currentIndex + 1).takeIf { it <= maxIndex } ?: 0
        configuration.initialTabIndex = targetIndex
        writable.update {
            it.copy(
                initialTabTitle = targetIndex
            )
        }
    }

    private fun onFullInfoPlayer() {
        val target = !configuration.fullInfoPlayer
        configuration.fullInfoPlayer = target
        writable.update {
            it.copy(
                fullInfoPlayer = target
            )
        }
    }

    private fun onSSLVerificationEnabled() {
        val target = !configuration.isSSLVerification
        configuration.isSSLVerification = target
        writable.update {
            it.copy(
                isSSLVerificationEnabled = target
            )
        }
    }

    private fun onAutoRefresh() {
        val target = !configuration.autoRefresh
        configuration.autoRefresh = target
        writable.update {
            it.copy(
                autoRefresh = target
            )
        }
    }

    private fun onScrollMode() {
        val target = !configuration.scrollMode
        configuration.scrollMode = target
        writable.update {
            it.copy(
                scrollMode = target
            )
        }
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
        configuration.clipMode = newValue
        writable.update {
            it.copy(
                clipMode = newValue
            )
        }
    }

    private fun onExperimentalMode() {
        val newValue = !configuration.experimentalMode
        if (!newValue) {
            // reset experimental ones to default value
            configuration.scrollMode = Configuration.DEFAULT_SCROLL_MODE
            writable.update {
                it.copy(
                    scrollMode = Configuration.DEFAULT_SCROLL_MODE
                )
            }
        }
        configuration.experimentalMode = newValue
        writable.update {
            it.copy(
                experimentalMode = newValue
            )
        }
    }

    private fun onGodMode() {
        val newValue = !configuration.godMode
        configuration.godMode = newValue
        writable.update {
            it.copy(
                godMode = newValue
            )
        }
    }

    private fun onConnectTimeout() {
        val newValue = when (configuration.connectTimeout) {
            ConnectTimeout.LONG -> ConnectTimeout.SHORT
            ConnectTimeout.SHORT -> ConnectTimeout.LONG
            else -> ConnectTimeout.SHORT
        }
        configuration.connectTimeout = newValue
        writable.update {
            it.copy(
                connectTimeout = newValue
            )
        }
    }

    private fun onUseCommonUIMode() {
        val newValue = !configuration.useCommonUIMode
        configuration.useCommonUIMode = newValue
        writable.update {
            it.copy(
                useCommonUIMode = newValue
            )
        }
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
        configuration.feedStrategy = newValue
        writable.update {
            it.copy(
                feedStrategy = newValue
            )
        }
    }

    private fun subscribe() {
        val title = writable.value.title
        if (title.isEmpty()) {
            writable.update {
                val message = context.getString(R.string.failed_empty_title)
                logger.log(message)
                it.copy(
                    adding = false,
                )
            }
            return
        }
        val url = readable.url
        val strategy = configuration.feedStrategy
        feedRepository.subscribe(title, url, strategy)
            .onEach { resource ->
                writable.update {
                    when (resource) {
                        Resource.Loading -> {
                            it.copy(adding = true)
                        }

                        is Resource.Success -> {
                            val message = context.getString(R.string.success_subscribe)
                            logger.log(message)
                            it.copy(
                                adding = false,
                                title = "",
                                url = "",
                            )
                        }

                        is Resource.Failure -> {
                            val message = resource.message.orEmpty()
                            logger.log(message)
                            it.copy(
                                adding = false
                            )
                        }
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    private fun createTabTitles(maxIndex: Int): List<String> = List(maxIndex + 1) {
        publisher.getTabTitle(it)
    }
}