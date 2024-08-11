package com.m3u.feature.setting

import android.net.Uri
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
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
import com.m3u.core.architecture.logger.Logger
import com.m3u.core.architecture.logger.Profiles
import com.m3u.core.architecture.logger.install
import com.m3u.core.architecture.preferences.Preferences
import com.m3u.core.unit.DataUnit
import com.m3u.core.unit.KB
import com.m3u.core.util.basic.startWithHttpScheme
import com.m3u.data.api.TvApiDelegate
import com.m3u.data.database.dao.ColorSchemeDao
import com.m3u.data.database.example.ColorSchemeExample
import com.m3u.data.database.model.ColorScheme
import com.m3u.data.database.model.DataSource
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.Channel
import com.m3u.data.parser.xtream.XtreamInput
import com.m3u.data.repository.playlist.PlaylistRepository
import com.m3u.data.repository.channel.ChannelRepository
import com.m3u.data.service.Messager
import com.m3u.data.service.PlayerManager
import com.m3u.data.worker.BackupWorker
import com.m3u.data.worker.RestoreWorker
import com.m3u.data.worker.SubscriptionWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import javax.inject.Inject

@HiltViewModel
class SettingViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val channelRepository: ChannelRepository,
    private val workManager: WorkManager,
    private val preferences: Preferences,
    private val messager: Messager,
    private val tvApi: TvApiDelegate,
    private val playerManager: PlayerManager,
    publisher: Publisher,
    // FIXME: do not use dao in viewmodel
    private val colorSchemeDao: ColorSchemeDao,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
    delegate: Logger
) : ViewModel() {
    private val logger = delegate.install(Profiles.VIEWMODEL_SETTING)

    internal val epgs: StateFlow<List<Playlist>> = playlistRepository
        .observeAllEpgs()
        .stateIn(
            scope = viewModelScope,
            initialValue = emptyList(),
            started = SharingStarted.WhileSubscribed(5_000L)
        )

    internal val hiddenChannels: StateFlow<List<Channel>> = channelRepository
        .observeAllHidden()
        .stateIn(
            scope = viewModelScope,
            initialValue = emptyList(),
            started = SharingStarted.WhileSubscribed(5_000L)
        )

    internal val hiddenCategoriesWithPlaylists: StateFlow<List<Pair<Playlist, String>>> =
        playlistRepository
            .observeAll()
            .map { playlists ->
                playlists
                    .filter { it.hiddenCategories.isNotEmpty() }
                    .flatMap { playlist -> playlist.hiddenCategories.map { playlist to it } }
            }
            .flowOn(ioDispatcher)
            .stateIn(
                scope = viewModelScope,
                initialValue = emptyList(),
                started = SharingStarted.WhileSubscribed(5_000L)
            )

    internal fun onUnhidePlaylistCategory(playlistUrl: String, group: String) {
        viewModelScope.launch {
            playlistRepository.hideOrUnhideCategory(playlistUrl, group)
        }
    }

    internal val colorSchemes: StateFlow<List<ColorScheme>> = combine(
        colorSchemeDao.observeAll().catch { emit(emptyList()) },
        snapshotFlow { preferences.followSystemTheme }
    ) { all, followSystemTheme -> if (followSystemTheme) all.filter { !it.isDark } else all }
        .flowOn(ioDispatcher)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    internal fun onClipboard(url: String) {
        val title = run {
            val filePath = url.split("/")
            val fileSplit = filePath.lastOrNull()?.split(".") ?: emptyList()
            fileSplit.firstOrNull() ?: "Playlist_${System.currentTimeMillis()}"
        }
        titleState.value = Uri.decode(title)
        urlState.value = Uri.decode(url)
        when (selectedState.value) {
            is DataSource.Xtream -> {
                val input = XtreamInput.decodeFromPlaylistUrlOrNull(url) ?: return
                basicUrlState.value = input.basicUrl
                usernameState.value = input.username
                passwordState.value = input.password
                titleState.value = Uri.decode("Xtream_${Clock.System.now().toEpochMilliseconds()}")
            }

            else -> {}
        }
    }

    internal fun onUnhideChannel(channelId: Int) {
        val hidden = hiddenChannels.value.find { it.id == channelId }
        if (hidden != null) {
            viewModelScope.launch {
                channelRepository.hide(channelId, false)
            }
        }
    }

    internal fun subscribe() {
        val title = titleState.value
        val url = urlState.value
        val uri = uriState.value
        val inputBasicUrl = basicUrlState.value
        val username = usernameState.value
        val password = passwordState.value
        val epg = epgState.value
        val selected = selectedState.value
        val localStorage = localStorageState.value
        val urlOrUri = uri
            .takeIf { uri != Uri.EMPTY }?.toString().orEmpty()
            .takeIf { localStorage }
            ?: url

        val basicUrl = if (inputBasicUrl.startWithHttpScheme()) inputBasicUrl
        else "http://$inputBasicUrl"

        when {
            forTvState.value -> {
                viewModelScope.launch {
                    tvApi.subscribe(
                        title,
                        urlOrUri,
                        basicUrl,
                        username,
                        password,
                        epg.ifBlank { null },
                        selected
                    )
                }
            }

            else -> when (selected) {
                DataSource.M3U -> {
                    if (title.isEmpty()) {
                        messager.emit(SettingMessage.EmptyTitle)
                        return
                    }
                    if (localStorage) {
                        if (uri == Uri.EMPTY) {
                            messager.emit(SettingMessage.EmptyFile)
                            return
                        }
                    } else {
                        if (url.isBlank()) {
                            messager.emit(SettingMessage.EmptyUrl)
                            return
                        }
                    }
                    SubscriptionWorker.m3u(workManager, title, urlOrUri)
                    messager.emit(SettingMessage.Enqueued)
                }

                DataSource.EPG -> {
                    if (title.isEmpty()) {
                        messager.emit(SettingMessage.EmptyEpgTitle)
                        return
                    }
                    if (epg.isEmpty()) {
                        messager.emit(SettingMessage.EmptyEpg)
                        return
                    }
                    viewModelScope.launch {
                        playlistRepository.insertEpgAsPlaylist(title, epg)
                    }
                    messager.emit(SettingMessage.EpgAdded)
                }

                DataSource.Xtream -> {
                    if (title.isEmpty()) {
                        messager.emit(SettingMessage.EmptyTitle)
                        return
                    }
                    SubscriptionWorker.xtream(
                        workManager,
                        title,
                        urlOrUri,
                        basicUrl,
                        username,
                        password
                    )
                    messager.emit(SettingMessage.Enqueued)
                }

                else -> return
            }
        }
        resetAllInputs()
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

    internal fun backup(uri: Uri) {
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

    internal fun restore(uri: Uri) {
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

    internal val cacheSpace: StateFlow<DataUnit> = playerManager
        .cacheSpace
        .map { DataUnit.of(it) }
        .stateIn(
            scope = viewModelScope,
            initialValue = 0.0.KB,
            started = SharingStarted.Lazily
        )

    private fun resetAllInputs() {
        titleState.value = ""
        urlState.value = ""
        uriState.value = Uri.EMPTY
        basicUrlState.value = ""
        usernameState.value = ""
        passwordState.value = ""
        epgState.value = ""
    }

    internal fun clearCache() {
        playerManager.clearCache()
    }

    internal fun deleteEpgPlaylist(epgUrl: String) {
        viewModelScope.launch {
            playlistRepository.deleteEpgPlaylistAndProgrammes(epgUrl)
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    internal fun applyColor(
        prev: ColorScheme?,
        argb: Int,
        isDark: Boolean
    ) {
        preferences.argb = argb
        preferences.darkMode = isDark
        viewModelScope.launch {
            if (prev != null) {
                colorSchemeDao.delete(prev)
            }
            colorSchemeDao.insert(
                ColorScheme(
                    argb = argb,
                    isDark = isDark,
                    name = "#${argb.toHexString(HexFormat.UpperCase)}"
                )
            )
        }
    }

    internal fun restoreSchemes() {
        val schemes = ColorSchemeExample.schemes
        viewModelScope.launch {
            colorSchemeDao.insertAll(*schemes.toTypedArray())
        }
    }

    internal val versionName: String = publisher.versionName
    internal val versionCode: Int = publisher.versionCode

    internal val titleState = mutableStateOf("")
    internal val urlState = mutableStateOf("")
    internal val uriState = mutableStateOf(Uri.EMPTY)
    internal val localStorageState = mutableStateOf(false)
    internal val forTvState = mutableStateOf(false)
    internal val basicUrlState = mutableStateOf("")
    internal val usernameState = mutableStateOf("")
    internal val passwordState = mutableStateOf("")
    internal val epgState = mutableStateOf("")
    internal val selectedState: MutableState<DataSource> = mutableStateOf(DataSource.M3U)
}
