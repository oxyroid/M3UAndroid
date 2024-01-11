package com.m3u.features.setting

import android.net.Uri
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.m3u.core.architecture.Publisher
import com.m3u.core.architecture.pref.Pref
import com.m3u.core.architecture.viewmodel.BaseViewModel
import com.m3u.data.database.model.Stream
import com.m3u.data.repository.StreamRepository
import com.m3u.data.repository.observeAll
import com.m3u.data.service.impl.SubscriptionWorker
import com.m3u.features.setting.fragments.ColorPack
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingViewModel @Inject constructor(
    private val streamRepository: StreamRepository,
    @Publisher.App private val publisher: Publisher,
    private val workManager: WorkManager,
    private val pref: Pref
) : BaseViewModel<SettingState, SettingEvent, SettingMessage>(
    emptyState = SettingState(
        versionName = publisher.versionName,
        versionCode = publisher.versionCode
    )
) {
    internal val banneds: StateFlow<ImmutableList<Stream>> = streamRepository
        .observeAll { it.banned }
        .map { it.toImmutableList() }
        .stateIn(
            scope = viewModelScope,
            initialValue = persistentListOf(),
            started = SharingStarted.WhileSubscribed(5_000L)
        )

    private val _packs = MutableStateFlow<ImmutableList<ColorPack>>(
        persistentListOf(
            ColorPack(0x5E6738, false, "avocado"),
            ColorPack(0x5E6738, true, "mint"),
            ColorPack(0xe69e71, false, "orange"),
            ColorPack(0xe69e71, true, "leather")
        )
    )
    internal val packs: StateFlow<ImmutableList<ColorPack>> = _packs.asStateFlow()

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

    private fun onBanned(streamId: Int) {
        val banned = banneds.value.find { it.id == streamId }
        if (banned != null) {
            viewModelScope.launch {
                streamRepository.ban(streamId, false)
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

        workManager.cancelAllWorkByTag(url)
        val request = OneTimeWorkRequestBuilder<SubscriptionWorker>()
            .setInputData(
                workDataOf(
                    SubscriptionWorker.INPUT_STRING_TITLE to title,
                    SubscriptionWorker.INPUT_STRING_URL to url,
                    SubscriptionWorker.INPUT_INT_STRATEGY to pref.playlistStrategy
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