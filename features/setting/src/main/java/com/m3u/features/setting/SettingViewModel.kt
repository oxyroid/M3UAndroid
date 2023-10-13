package com.m3u.features.setting

import android.app.Application
import android.net.Uri
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.m3u.core.annotation.ClipMode
import com.m3u.core.annotation.ConnectTimeout
import com.m3u.core.annotation.FeedStrategy
import com.m3u.core.architecture.Logger
import com.m3u.core.architecture.Publisher
import com.m3u.core.architecture.configuration.Configuration
import com.m3u.core.architecture.viewmodel.BaseViewModel
import com.m3u.data.repository.LiveRepository
import com.m3u.data.repository.observeAll
import com.m3u.data.worker.SubscriptionInBackgroundWorker
import com.m3u.ui.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.m3u.i18n.R as I18R

@HiltViewModel
class SettingViewModel @Inject constructor(
    private val liveRepository: LiveRepository,
    @Publisher.App private val publisher: Publisher,
    private val workManager: WorkManager,
    application: Application,
    configuration: Configuration,
    @Logger.Ui private val logger: Logger
) : BaseViewModel<SettingState, SettingEvent>(
    application = application,
    emptyState = SettingState(
        version = publisher.versionName,
        configuration = configuration
    )
) {
    init {
        liveRepository
            .observeAll { it.banned }
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
            SettingEvent.ScrollDefaultDestination -> scrollDefaultDestination()
            is SettingEvent.ImportJavaScript -> importJavaScript(event.uri)
            SettingEvent.OnLocalStorage -> onLocalStorage()
            is SettingEvent.OpenDocument -> openDocument(event.uri)
        }
    }

    private fun openDocument(uri: Uri?) {
        writable.update {
            it.copy(
                uri = uri ?: Uri.EMPTY
            )
        }
    }

    private fun importJavaScript(uri: Uri?) {
    }

    private fun scrollDefaultDestination() {
        val total = Destination.Root.entries.size
        val current = readable.initialRootDestination
        val target = (current + 1).takeIf { it < total } ?: 0
        readable.initialRootDestination = target
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
            val message = string(I18R.string.feat_setting_error_empty_title)
            logger.log(message)
            return
        }
        val strategy = readable.feedStrategy
        val url = readable.actualUrl
        if (url == null) {
            val message = when {
                readable.localStorage -> string(I18R.string.feat_setting_error_unselected_file)
                else -> string(I18R.string.feat_setting_error_blank_url)
            }
            logger.log(message)
            return
        }

        workManager.cancelAllWorkByTag(url)
        val request = OneTimeWorkRequestBuilder<SubscriptionInBackgroundWorker>()
            .setInputData(
                workDataOf(
                    SubscriptionInBackgroundWorker.INPUT_STRING_TITLE to title,
                    SubscriptionInBackgroundWorker.INPUT_STRING_URL to url,
                    SubscriptionInBackgroundWorker.INPUT_INT_STRATEGY to strategy
                )
            )
            .addTag(url)
            .build()
        workManager.enqueue(request)
        val message = string(I18R.string.feat_setting_enqueue_subscribe)
        logger.log(message)
        writable.update {
            it.copy(
                title = "",
                url = "",
                uri = Uri.EMPTY
            )
        }
    }

    private fun onLocalStorage() {
        writable.update {
            it.copy(
                localStorage = !it.localStorage
            )
        }
    }
}