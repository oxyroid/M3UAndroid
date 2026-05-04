package com.m3u.business.setting

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import androidx.work.workDataOf
import com.m3u.core.foundation.architecture.Publisher
import com.m3u.core.foundation.architecture.preferences.PreferencesKeys
import com.m3u.core.foundation.architecture.preferences.Settings
import com.m3u.core.foundation.architecture.preferences.flowOf
import com.m3u.core.foundation.architecture.preferences.set
import com.m3u.core.foundation.util.basic.startWithHttpScheme
import com.m3u.data.api.TvApiDelegate
import com.m3u.data.codec.CodecPackInstallResult
import com.m3u.data.codec.CodecPackRepository
import com.m3u.data.database.dao.ColorSchemeDao
import com.m3u.data.database.example.ColorSchemeExample
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.ColorScheme
import com.m3u.data.database.model.DataSource
import com.m3u.data.database.model.Playlist
import com.m3u.data.parser.xtream.XtreamInput
import com.m3u.data.repository.channel.ChannelRepository
import com.m3u.data.repository.playlist.PlaylistRepository
import com.m3u.data.repository.tv.TvRepository
import com.m3u.data.service.Messager
import com.m3u.data.worker.BackupWorker
import com.m3u.data.worker.RestoreWorker
import com.m3u.data.worker.SubscriptionWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Clock

@HiltViewModel
class SettingViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val channelRepository: ChannelRepository,
    private val workManager: WorkManager,
    private val settings: Settings,
    private val messager: Messager,
    private val tvRepository: TvRepository,
    private val tvApi: TvApiDelegate,
    private val codecPackRepository: CodecPackRepository,
    publisher: Publisher,
    // FIXME: do not use dao in viewmodel
    private val colorSchemeDao: ColorSchemeDao,
) : ViewModel() {
    private val _codecPackState = MutableStateFlow(codecPackRepository.toPendingState())
    val codecPackState: StateFlow<CodecPackState> = _codecPackState

    init {
        refreshCodecPack()
    }

    val epgs: StateFlow<List<Playlist>> = playlistRepository
        .observeAllEpgs()
        .stateIn(
            scope = viewModelScope,
            initialValue = emptyList(),
            started = SharingStarted.WhileSubscribed(5_000L)
        )

    val hiddenChannels: StateFlow<List<Channel>> = channelRepository
        .observeAllHidden()
        .stateIn(
            scope = viewModelScope,
            initialValue = emptyList(),
            started = SharingStarted.WhileSubscribed(5_000L)
        )

    val hiddenCategoriesWithPlaylists: StateFlow<List<Pair<Playlist, String>>> =
        playlistRepository
            .observeAll()
            .map { playlists ->
                playlists
                    .filter { it.hiddenCategories.isNotEmpty() }
                    .flatMap { playlist -> playlist.hiddenCategories.map { playlist to it } }
            }
            .flowOn(Dispatchers.Default)
            .stateIn(
                scope = viewModelScope,
                initialValue = emptyList(),
                started = SharingStarted.WhileSubscribed(5_000L)
            )

    fun onUnhidePlaylistCategory(playlistUrl: String, group: String) {
        viewModelScope.launch {
            playlistRepository.hideOrUnhideCategory(playlistUrl, group)
        }
    }

    fun refreshCodecPack() {
        viewModelScope.launch(Dispatchers.IO) {
            _codecPackState.value = codecPackRepository.toState()
        }
    }

    fun installCodecPack() {
        if (!_codecPackState.value.enabled) return
        if (_codecPackState.value.installing) return
        _codecPackState.value = _codecPackState.value.copy(installing = true, error = null)
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                codecPackRepository.installFromDefaultSnapshot()
            }.fold(
                onSuccess = { result ->
                    _codecPackState.value = codecPackRepository.toState().copy(
                        error = when (result) {
                            is CodecPackInstallResult.UnsupportedAbi -> result.supportedAbis.joinToString()
                            else -> null
                        }
                    )
                },
                onFailure = { error ->
                    _codecPackState.value = codecPackRepository.toState().copy(error = error.message)
                }
            )
        }
    }

    fun deleteCodecPack() {
        viewModelScope.launch(Dispatchers.IO) {
            codecPackRepository.deleteInstalledPack()
            _codecPackState.value = codecPackRepository.toState()
        }
    }

    private fun CodecPackRepository.toState(): CodecPackState {
        return CodecPackState(
            packId = packId,
            enabled = enabled,
            abi = currentAbi,
            installed = isInstalled()
        )
    }

    private fun CodecPackRepository.toPendingState(): CodecPackState {
        return CodecPackState(
            packId = packId,
            enabled = enabled,
            abi = currentAbi
        )
    }

    val colorSchemes: StateFlow<List<ColorScheme>> = combine(
        colorSchemeDao.observeAll().catch { emit(emptyList()) },
        settings.flowOf(PreferencesKeys.FOLLOW_SYSTEM_THEME)
    ) { all, followSystemTheme -> if (followSystemTheme) all.filter { !it.isDark } else all }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun onClipboard(url: String) {
        val title = run {
            val filePath = url.split("/")
            val fileSplit = filePath.lastOrNull()?.split(".") ?: emptyList()
            fileSplit.firstOrNull() ?: "Playlist_${System.currentTimeMillis()}"
        }
        properties.titleState.value = Uri.decode(title)
        properties.urlState.value = Uri.decode(url)
        when (properties.selectedState.value) {
            is DataSource.Xtream -> {
                val input = XtreamInput.decodeFromPlaylistUrlOrNull(url) ?: return
                properties.basicUrlState.value = input.basicUrl
                properties.usernameState.value = input.username
                properties.passwordState.value = input.password
                properties.titleState.value = Uri.decode("Xtream_${Clock.System.now().toEpochMilliseconds()}")
            }

            else -> {}
        }
    }

    fun onUnhideChannel(channelId: Int) {
        val hidden = hiddenChannels.value.find { it.id == channelId }
        if (hidden != null) {
            viewModelScope.launch {
                channelRepository.hide(channelId, false)
            }
        }
    }

    fun subscribe() {
        val title = properties.titleState.value
        val url = properties.urlState.value
        val uri = properties.uriState.value
        val inputBasicUrl = properties.basicUrlState.value
        val username = properties.usernameState.value
        val password = properties.passwordState.value
        val epg = properties.epgState.value
        val selected = properties.selectedState.value
        val localStorage = properties.localStorageState.value
        val forTv = properties.forTvState.value
        val urlOrUri = uri
            .takeIf { uri != Uri.EMPTY }?.toString().orEmpty()
            .takeIf { localStorage }
            ?: url

        val basicUrl = if (inputBasicUrl.startWithHttpScheme()) inputBasicUrl
        else "http://$inputBasicUrl"

        if (forTv) {
            subscribeForTv(
                selected = selected,
                title = title,
                url = url,
                basicUrl = basicUrl,
                username = username,
                password = password,
                epg = epg
            )
            return
        }

        when (selected) {
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
        resetAllInputs()
    }

    private fun subscribeForTv(
        selected: DataSource,
        title: String,
        url: String,
        basicUrl: String,
        username: String,
        password: String,
        epg: String
    ) {
        if (tvRepository.connected.value == null) {
            messager.emit(SettingMessage.RemoteTvNotConnected)
            return
        }

        when (selected) {
            DataSource.M3U -> {
                if (title.isEmpty()) {
                    messager.emit(SettingMessage.EmptyTitle)
                    return
                }
                if (url.isBlank()) {
                    messager.emit(SettingMessage.EmptyUrl)
                    return
                }
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
            }

            DataSource.Xtream -> {
                if (title.isEmpty()) {
                    messager.emit(SettingMessage.EmptyTitle)
                    return
                }
            }

            else -> return
        }

        viewModelScope.launch {
            val result = runCatching {
                tvApi.subscribe(
                    title = title,
                    url = url.ifBlank { basicUrl },
                    basicUrl = basicUrl,
                    username = username,
                    password = password,
                    epg = epg.ifBlank { null },
                    dataSource = selected
                )
            }.getOrNull()
            if (result?.result == true) {
                messager.emit(SettingMessage.RemoteTvSubscribeSent)
                resetAllInputs()
            } else {
                messager.emit(SettingMessage.RemoteTvSubscribeFailed)
            }
        }
    }

    val backingUpOrRestoring: StateFlow<BackingUpAndRestoringState> = workManager
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
        .flowOn(Dispatchers.Default)
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

    private fun resetAllInputs() {
        with(properties) {
            titleState.value = ""
            urlState.value = ""
            uriState.value = Uri.EMPTY
            basicUrlState.value = ""
            usernameState.value = ""
            passwordState.value = ""
            epgState.value = ""
        }
    }

    fun deleteEpgPlaylist(epgUrl: String) {
        viewModelScope.launch {
            playlistRepository.deleteEpgPlaylistAndProgrammes(epgUrl)
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun applyColor(
        prev: ColorScheme?,
        argb: Int,
        isDark: Boolean
    ) {
        viewModelScope.launch {
            settings[PreferencesKeys.DARK_MODE] = isDark
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

    fun restoreSchemes() {
        val schemes = ColorSchemeExample.schemes
        viewModelScope.launch {
            colorSchemeDao.insertAll(*schemes.toTypedArray())
        }
    }

    val versionName: String = publisher.versionName
    val versionCode: Int = publisher.versionCode

    val properties = SettingProperties()
}
