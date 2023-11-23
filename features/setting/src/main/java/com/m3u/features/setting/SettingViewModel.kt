package com.m3u.features.setting

import android.net.Uri
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.m3u.core.annotation.ClipMode
import com.m3u.core.annotation.ConnectTimeout
import com.m3u.core.annotation.FeedStrategy
import com.m3u.core.architecture.Publisher
import com.m3u.core.architecture.configuration.Configuration
import com.m3u.core.architecture.viewmodel.BaseViewModel
import com.m3u.data.repository.LiveRepository
import com.m3u.data.repository.observeAll
import com.m3u.data.worker.SubscriptionWorker
import com.m3u.ui.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingViewModel @Inject constructor(
    private val liveRepository: LiveRepository,
    @Publisher.App private val publisher: Publisher,
    private val workManager: WorkManager,
    configuration: Configuration
) : BaseViewModel<SettingState, SettingEvent, SettingMessage>(
    emptyState = SettingState(
        versionName = publisher.versionName,
        versionCode = publisher.versionCode,
        configuration = configuration
    )
) {
    init {
        liveRepository
            .observeAll { it.banned }
            .onEach { lives ->
                writable.update {
                    it.copy(
                        banneds = lives
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
            is SettingEvent.OnBanned -> onBanned(event.id)
            SettingEvent.ScrollDefaultDestination -> scrollDefaultDestination()
            is SettingEvent.ImportJavaScript -> importJavaScript(event.uri)
            SettingEvent.OnLocalStorage -> onLocalStorage()
            is SettingEvent.OpenDocument -> openDocument(event.uri)
        }
    }

    private fun openDocument(uri: Uri) {
        writable.update {
            it.copy(
                uri = uri
            )
        }
    }

    private fun importJavaScript(uri: Uri) {
    }

    private fun scrollDefaultDestination() {
        val total = Destination.Root.entries.size
        val current = readable.initialRootDestination
        val target = (current + 1).takeIf { it < total } ?: 0
        readable.initialRootDestination = target
    }

    private fun onBanned(liveId: Int) {
        val banned = readable.banneds.find { it.id == liveId }
        if (banned != null) {
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
            onMessage(SettingMessage.EmptyTitle)
            return
        }
        val url = readable.actualUrl
        if (url == null) {
            val warning = when {
                readable.localStorage -> SettingMessage.EmptyFile
                else -> SettingMessage.EmptyUrl
            }
            onMessage(warning)
            return
        }

        val strategy = readable.feedStrategy
        workManager.cancelAllWorkByTag(url)
        val request = OneTimeWorkRequestBuilder<SubscriptionWorker>()
            .setInputData(
                workDataOf(
                    SubscriptionWorker.INPUT_STRING_TITLE to title,
                    SubscriptionWorker.INPUT_STRING_URL to url,
                    SubscriptionWorker.INPUT_INT_STRATEGY to strategy
                )
            )
            .addTag(url)
            .build()
        workManager.enqueue(request)
        onMessage(SettingMessage.Enqueued)
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