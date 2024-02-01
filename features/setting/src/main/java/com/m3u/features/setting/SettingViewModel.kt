package com.m3u.features.setting

import android.net.Uri
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.m3u.core.architecture.Publisher
import com.m3u.core.architecture.pref.Pref
import com.m3u.core.architecture.viewmodel.BaseViewModel
import com.m3u.data.database.dao.ColorPackDao
import com.m3u.data.database.model.ColorPack
import com.m3u.data.database.model.Stream
import com.m3u.data.manager.MessageManager
import com.m3u.data.manager.internal.SubscriptionWorker
import com.m3u.data.repository.StreamRepository
import com.m3u.data.repository.observeAll
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
    private val pref: Pref,
    private val messageManager: MessageManager,
    colorPackDao: ColorPackDao
) : BaseViewModel<SettingState, SettingEvent>(
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

    internal val packs: StateFlow<ImmutableList<ColorPack>> = colorPackDao
        .observeAllColorPacks()
        .map { it.toImmutableList() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = persistentListOf()
        )

    override fun onEvent(event: SettingEvent) {
        when (event) {
            SettingEvent.Subscribe -> subscribe()
            is SettingEvent.OnTitle -> onTitle(event.title)
            is SettingEvent.OnUrl -> onUrl(event.url)
            is SettingEvent.OnBanned -> onBanned(event.id)
            SettingEvent.OnLocalStorage -> onLocalStorage()
            is SettingEvent.OpenDocument -> openDocument(event.uri)
        }
    }

    internal fun onClipboard(url: String) {
        val title = run {
            val filePath = url.split("/")
            val fileSplit = filePath.lastOrNull()?.split(".") ?: emptyList()
            fileSplit.firstOrNull() ?: "Playlist_${System.currentTimeMillis()}"
        }
        onTitle(title)
        onUrl(url)
    }

    private fun openDocument(uri: Uri) {
        writable.update {
            it.copy(
                uri = uri
            )
        }
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
            messageManager.emit(SettingMessage.EmptyTitle)
            return
        }
        val url = readable.actualUrl
        if (url == null) {
            val warning = when {
                readable.localStorage -> SettingMessage.EmptyFile
                else -> SettingMessage.EmptyUrl
            }
            messageManager.emit(warning)
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
        messageManager.emit(SettingMessage.Enqueued)
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