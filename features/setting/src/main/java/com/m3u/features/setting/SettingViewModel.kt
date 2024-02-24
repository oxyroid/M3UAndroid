package com.m3u.features.setting

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import androidx.work.workDataOf
import com.m3u.core.architecture.Publisher
import com.m3u.core.architecture.dispatcher.Dispatcher
import com.m3u.core.architecture.dispatcher.M3uDispatchers.IO
import com.m3u.core.architecture.pref.Pref
import com.m3u.core.architecture.pref.observeAsFlow
import com.m3u.core.architecture.viewmodel.BaseViewModel
import com.m3u.core.util.basic.startsWithAny
import com.m3u.data.api.LocalPreparedService
import com.m3u.data.database.dao.ColorPackDao
import com.m3u.data.database.model.ColorPack
import com.m3u.data.database.model.DataSource
import com.m3u.data.database.model.Stream
import com.m3u.data.parser.XtreamParser
import com.m3u.data.repository.StreamRepository
import com.m3u.data.repository.observeAll
import com.m3u.data.service.Messager
import com.m3u.data.worker.BackupWorker
import com.m3u.data.worker.RestoreWorker
import com.m3u.data.worker.SubscriptionWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingViewModel @Inject constructor(
    private val streamRepository: StreamRepository,
    private val workManager: WorkManager,
    pref: Pref,
    private val messager: Messager,
    private val localService: LocalPreparedService,
    publisher: Publisher,
    colorPackDao: ColorPackDao,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher
) : BaseViewModel<SettingState, SettingEvent>(
    emptyState = SettingState(
        versionName = publisher.versionName,
        versionCode = publisher.versionCode,
    )
) {
    internal val hiddenStreams: StateFlow<ImmutableList<Stream>> = streamRepository
        .observeAll { it.hidden }
        .map { it.toImmutableList() }
        .flowOn(ioDispatcher)
        .stateIn(
            scope = viewModelScope,
            initialValue = persistentListOf(),
            started = SharingStarted.WhileSubscribed(5_000L)
        )

    internal val packs: StateFlow<ImmutableList<ColorPack>> = combine(
        colorPackDao.observeAllColorPacks(),
        pref.observeAsFlow { it.followSystemTheme }
    ) { all, followSystemTheme -> if (followSystemTheme) all.filter { !it.isDark } else all }
        .map { it.toImmutableList() }
        .flowOn(ioDispatcher)
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
            is SettingEvent.OnHidden -> onHidden(event.id)
            SettingEvent.OnLocalStorage -> onLocalStorage()
            is SettingEvent.OpenDocument -> openDocument(event.uri)
        }
    }

    internal fun onClipboard(url: String) {
        when (selected) {
            DataSource.M3U -> {
                val title = run {
                    val filePath = url.split("/")
                    val fileSplit = filePath.lastOrNull()?.split(".") ?: emptyList()
                    fileSplit.firstOrNull() ?: "Playlist_${System.currentTimeMillis()}"
                }
                onTitle(title)
                onUrl(url)
            }

            DataSource.Xtream -> {
                val input = runCatching { XtreamParser.decodeXtreamInput(url) }
                    .getOrNull() ?: return
                address = input.address
                username = input.username
                password = input.password
            }

            else -> {}
        }
    }

    private fun openDocument(uri: Uri) {
        writable.update {
            it.copy(
                uri = uri
            )
        }
    }

    private fun onHidden(streamId: Int) {
        val hidden = hiddenStreams.value.find { it.id == streamId }
        if (hidden != null) {
            viewModelScope.launch {
                streamRepository.hide(streamId, false)
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
            messager.emit(SettingMessage.EmptyTitle)
            return
        }
        val url = readable.actualUrl

        val addressWithScheme = if (address.startWithScheme()) address
        else "http://$address"

        if (forTv) {
            viewModelScope.launch {
                localService.subscribe(
                    title,
                    url,
                    addressWithScheme,
                    username,
                    password,
                    selected
                )
            }
            clearAllInputs()
            return
        }
        workManager.cancelAllWorkByTag(url)
        workManager.cancelAllWorkByTag(addressWithScheme)
        val request = OneTimeWorkRequestBuilder<SubscriptionWorker>()
            .setInputData(
                workDataOf(
                    SubscriptionWorker.INPUT_STRING_TITLE to title,
                    SubscriptionWorker.INPUT_STRING_URL to url,
                    SubscriptionWorker.INPUT_STRING_ADDRESS to addressWithScheme,
                    SubscriptionWorker.INPUT_STRING_USERNAME to username,
                    SubscriptionWorker.INPUT_STRING_PASSWORD to password,
                    SubscriptionWorker.INPUT_STRING_DATA_SOURCE_VALUE to selected.value
                )
            )
            .addTag(url)
            .addTag(addressWithScheme)
            .addTag(SubscriptionWorker.TAG)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        workManager.enqueue(request)
        messager.emit(SettingMessage.Enqueued)
        clearAllInputs()
    }

    private fun onLocalStorage() {
        writable.update {
            it.copy(
                localStorage = !it.localStorage
            )
        }
    }

    internal val backingUpOrRestoring: StateFlow<BackingUpAndRestoringState> = workManager
        .getWorkInfosFlow(
            WorkQuery.fromStates(
                WorkInfo.State.RUNNING,
                WorkInfo.State.ENQUEUED
            )
        )
        .mapLatest { infos ->
            var backingUp = false
            var restoring = false
            for (info in infos) {
                if (backingUp && restoring) break
                for (tag in info.tags) {
                    if (backingUp && restoring) break
                    if (tag == BackupWorker.TAG) backingUp = true
                    if (tag == RestoreWorker.TAG) restoring = true
                }
            }
            BackingUpAndRestoringState.of(backingUp, restoring)
        }
        .flowOn(ioDispatcher)
        .stateIn(
            scope = viewModelScope,
            // determine ui button enabled or not
            // both as default
            initialValue = BackingUpAndRestoringState.BOTH,
            started = SharingStarted.WhileSubscribed(5000)
        )

    fun backup(uri: Uri) {
        workManager.cancelAllWorkByTag(BackupWorker.TAG)
        val request = OneTimeWorkRequestBuilder<BackupWorker>()
            .setInputData(
                workDataOf(
                    BackupWorker.INPUT_URI to uri.toString()
                )
            )
            .addTag(BackupWorker.TAG)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        workManager.enqueue(request)
        messager.emit(SettingMessage.BackingUp)
    }

    fun restore(uri: Uri) {
        workManager.cancelAllWorkByTag(RestoreWorker.TAG)
        val request = OneTimeWorkRequestBuilder<RestoreWorker>()
            .setInputData(
                workDataOf(
                    RestoreWorker.INPUT_URI to uri.toString()
                )
            )
            .addTag(RestoreWorker.TAG)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        workManager.enqueue(request)
        messager.emit(SettingMessage.Restoring)
    }

    private fun String.startWithScheme(): Boolean =
        startsWithAny("http://", "https://", ignoreCase = true)

    private fun clearAllInputs() {
        writable.update {
            it.copy(
                title = "",
                url = "",
                uri = Uri.EMPTY
            )
        }
        address = ""
        username = ""
        password = ""
    }

    internal var forTv by mutableStateOf(false)
    internal var selected by mutableStateOf(DataSource.M3U)
    internal var address by mutableStateOf("")
    internal var username by mutableStateOf("")
    internal var password by mutableStateOf("")
}