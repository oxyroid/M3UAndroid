package com.m3u.features.setting

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.m3u.core.architecture.Publisher
import com.m3u.core.architecture.configuration.Configuration
import com.m3u.core.architecture.viewmodel.BaseViewModel
import com.m3u.data.repository.LiveRepository
import com.m3u.data.repository.observeAll
import com.m3u.data.worker.SubscriptionWorker
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
    private val configuration: Configuration
) : BaseViewModel<SettingState, SettingEvent, SettingMessage>(
    emptyState = SettingState(
        versionName = publisher.versionName,
        versionCode = publisher.versionCode
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
            is SettingEvent.OnBanned -> onBanned(event.id)
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

    private fun onBanned(liveId: Int) {
        val banned = readable.banneds.find { it.id == liveId }
        if (banned != null) {
            viewModelScope.launch {
                liveRepository.setBanned(liveId, false)
            }
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

        val strategy by configuration.feedStrategy
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