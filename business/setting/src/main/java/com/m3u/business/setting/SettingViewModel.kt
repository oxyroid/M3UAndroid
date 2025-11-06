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
import com.m3u.core.architecture.Publisher
import com.m3u.core.architecture.preferences.PreferencesKeys
import com.m3u.core.architecture.preferences.Settings
import com.m3u.core.architecture.preferences.flowOf
import com.m3u.core.architecture.preferences.set
import com.m3u.core.util.basic.startWithHttpScheme
import com.m3u.data.database.dao.ColorSchemeDao
import com.m3u.data.database.example.ColorSchemeExample
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.ColorScheme
import com.m3u.data.database.model.DataSource
import com.m3u.data.database.model.Playlist
import com.m3u.data.parser.xtream.XtreamInput
import com.m3u.data.repository.channel.ChannelRepository
import com.m3u.data.repository.playlist.PlaylistRepository
import com.m3u.data.repository.usbkey.USBKeyRepository
import com.m3u.data.repository.encryption.PINEncryptionRepository
import com.m3u.data.service.Messager
import com.m3u.data.worker.BackupWorker
import com.m3u.data.worker.RestoreWorker
import com.m3u.data.worker.SubscriptionWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
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
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SettingViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val channelRepository: ChannelRepository,
    private val workManager: WorkManager,
    private val settings: Settings,
    private val messager: Messager,
    private val usbKeyRepository: USBKeyRepository,
    private val pinEncryptionRepository: PINEncryptionRepository,
    val metricsCalculator: com.m3u.data.security.EncryptionMetricsCalculator,
    publisher: Publisher,
    // FIXME: do not use dao in viewmodel
    private val colorSchemeDao: ColorSchemeDao,
) : ViewModel() {
    private val timber = Timber.tag("SettingViewModel")

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
        val urlOrUri = uri
            .takeIf { uri != Uri.EMPTY }?.toString().orEmpty()
            .takeIf { localStorage }
            ?: url

        val basicUrl = if (inputBasicUrl.startWithHttpScheme()) inputBasicUrl
        else "http://$inputBasicUrl"

        when {
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

                DataSource.WebDrop -> {
                    messager.emit(SettingMessage.WebDropNoSubscribe)
                    return
                }

                else -> return
            }
        }
        resetAllInputs()
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

    val usbKeyState = usbKeyRepository.state
        .stateIn(
            scope = viewModelScope,
            initialValue = com.m3u.data.repository.usbkey.USBKeyState(),
            started = SharingStarted.WhileSubscribed(5_000L)
        )

    fun enableUSBEncryption() {
        viewModelScope.launch {
            timber.d("=== enableUSBEncryption() CALLED ===")
            timber.d("Current USB state: ${usbKeyState.value}")
            timber.d("Is connected: ${usbKeyState.value.isConnected}")
            timber.d("Device name: ${usbKeyState.value.deviceName}")
            timber.d("Is encryption enabled: ${usbKeyState.value.isEncryptionEnabled}")

            if (!usbKeyState.value.isConnected) {
                timber.w("USB not connected - sending USBNotConnected message")
                messager.emit(SettingMessage.USBNotConnected)
                return@launch
            }

            timber.d("Calling usbKeyRepository.initializeEncryption()...")
            val result = usbKeyRepository.initializeEncryption()

            result.onSuccess {
                timber.d("✓ USB encryption initialized successfully")
                messager.emit(SettingMessage.USBEncryptionEnabled)
            }.onFailure { error ->
                timber.e(error, "✗ USB encryption initialization failed")
                timber.e("Error type: ${error.javaClass.simpleName}")
                timber.e("Error message: ${error.message}")
                timber.e("Stack trace: ${error.stackTraceToString()}")
                messager.emit(SettingMessage.USBEncryptionError(error.message ?: "Unknown error"))
            }

            timber.d("=== enableUSBEncryption() COMPLETED ===")
        }
    }

    fun disableUSBEncryption() {
        viewModelScope.launch {
            usbKeyRepository.disableEncryption().onSuccess {
                messager.emit(SettingMessage.USBEncryptionDisabled)
            }.onFailure { error ->
                messager.emit(SettingMessage.USBEncryptionError(error.message ?: "Unknown error"))
            }
        }
    }

    fun requestUSBPermission() {
        viewModelScope.launch {
            usbKeyRepository.requestUSBPermission().onFailure { error ->
                messager.emit(SettingMessage.USBEncryptionError(error.message ?: "Permission denied"))
            }
        }
    }

    // ========================================
    // PIN Encryption Methods
    // ========================================

    /**
     * Enable database encryption with a 6-digit PIN
     * @param pin Must be exactly 6 digits
     */
    fun enablePINEncryption(pin: String) {
        viewModelScope.launch {
            timber.d("=== enablePINEncryption() CALLED ===")
            timber.d("PIN length: ${pin.length}")

            // Validate PIN format
            if (!pinEncryptionRepository.isValidPIN(pin)) {
                timber.w("Invalid PIN format")
                messager.emit(SettingMessage.PINInvalid)
                return@launch
            }

            timber.d("Calling pinEncryptionRepository.initializeEncryption()...")
            val result = pinEncryptionRepository.initializeEncryption(pin)

            result.onSuccess {
                timber.d("✓ PIN encryption initialized successfully")
                messager.emit(SettingMessage.PINEncryptionEnabled)
            }.onFailure { error ->
                timber.e(error, "✗ PIN encryption initialization failed")
                timber.e("Error type: ${error.javaClass.simpleName}")
                timber.e("Error message: ${error.message}")
                messager.emit(SettingMessage.PINEncryptionError(error.message ?: "Unknown error"))
            }

            timber.d("=== enablePINEncryption() COMPLETED ===")
        }
    }

    /**
     * Unlock the encrypted database with PIN
     * @param pin The 6-digit PIN
     */
    fun unlockWithPIN(pin: String) {
        viewModelScope.launch {
            timber.d("=== unlockWithPIN() CALLED ===")

            val result = pinEncryptionRepository.unlockWithPIN(pin)

            result.onSuccess {
                timber.d("✓ Database unlocked successfully")
                messager.emit(SettingMessage.PINUnlocked)
            }.onFailure { error ->
                timber.w("✗ PIN unlock failed: ${error.message}")
                messager.emit(SettingMessage.PINIncorrect)
            }

            timber.d("=== unlockWithPIN() COMPLETED ===")
        }
    }

    /**
     * Disable PIN encryption and decrypt database
     * @param pin Current PIN for verification
     */
    fun disablePINEncryption(pin: String) {
        viewModelScope.launch {
            timber.d("=== disablePINEncryption() CALLED ===")

            val result = pinEncryptionRepository.disableEncryption(pin)

            result.onSuccess {
                timber.d("✓ PIN encryption disabled successfully")
                messager.emit(SettingMessage.PINEncryptionDisabled)
            }.onFailure { error ->
                timber.e(error, "✗ PIN encryption disable failed")
                if (error is SecurityException) {
                    messager.emit(SettingMessage.PINIncorrect)
                } else {
                    messager.emit(SettingMessage.PINEncryptionError(error.message ?: "Unknown error"))
                }
            }

            timber.d("=== disablePINEncryption() COMPLETED ===")
        }
    }

    /**
     * Check if PIN encryption is currently enabled
     */
    suspend fun isPINEncryptionEnabled(): Boolean {
        return pinEncryptionRepository.isEncryptionEnabled()
    }

    /**
     * Get current encryption progress (if any operation is in progress)
     */
    suspend fun getPINEncryptionProgress() = pinEncryptionRepository.getEncryptionProgress()

    val properties = SettingProperties()
}
