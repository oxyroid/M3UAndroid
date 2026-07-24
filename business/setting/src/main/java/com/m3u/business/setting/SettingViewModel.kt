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
import com.m3u.data.repository.extension.ExtensionSettingEditToken
import com.m3u.data.repository.extension.ExtensionSettingUpdateResult
import com.m3u.data.repository.extension.ExtensionSettingsConfiguration
import com.m3u.data.repository.extension.ExtensionSettingsRepository
import com.m3u.data.repository.playlist.PlaylistRepository
import com.m3u.data.repository.provider.DiscoveredSubscriptionProvider
import com.m3u.data.repository.provider.ProviderAccountSummary
import com.m3u.data.repository.provider.ProviderDiscoveryException
import com.m3u.data.repository.provider.ProviderSubscriptionRequest
import com.m3u.data.repository.provider.SubscriptionProviderRepository
import com.m3u.data.repository.plugin.ExtensionPluginRepository
import com.m3u.data.repository.plugin.InstalledPlugin
import com.m3u.data.repository.plugin.PluginAuthorizationToken
import com.m3u.data.repository.plugin.PluginDataClearResult
import com.m3u.data.repository.plugin.PluginEnableResult
import com.m3u.data.repository.tv.TvRepository
import com.m3u.data.service.Messager
import com.m3u.data.worker.BackupWorker
import com.m3u.data.worker.RestoreWorker
import com.m3u.data.worker.SubscriptionWorker
import com.m3u.extension.api.subscription.EmbyCompatibleProviderKinds
import com.m3u.extension.api.subscription.ProviderKind
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.subscription.SubscriptionProviderSettingKeys
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.time.Clock

@HiltViewModel
class SettingViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val channelRepository: ChannelRepository,
    private val subscriptionProviderRepository: SubscriptionProviderRepository,
    private val extensionPluginRepository: ExtensionPluginRepository,
    private val extensionSettingsRepository: ExtensionSettingsRepository,
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
    private var providerSubscriptionJob: Job? = null
    private var providerDiscoveryJob: Job? = null
    private var providerReauthenticationJob: Job? = null
    private var extensionSettingsLoadJob: Job? = null
    private var extensionSettingsRequestedId: ExtensionId? = null
    private var extensionSettingsGeneration = 0L
    private var extensionSettingsUpdateGeneration = 0L
    private val extensionSettingsOperationQueue = ExtensionSettingsOperationQueue(
        scope = viewModelScope,
        onFailure = { messager.emit(SettingMessage.ExtensionOperationFailed) },
    )

    private val _extensionPlugins = MutableStateFlow<List<InstalledPlugin>>(emptyList())
    val extensionPlugins: StateFlow<List<InstalledPlugin>> = _extensionPlugins

    private val _extensionSettings = MutableStateFlow<ExtensionSettingsConfiguration?>(null)
    val extensionSettings: StateFlow<ExtensionSettingsConfiguration?> = _extensionSettings

    private val _extensionDiagnostics = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val extensionDiagnostics = _extensionDiagnostics.asSharedFlow()

    private val _providerDiscoveryState = MutableStateFlow<ProviderDiscoveryState>(
        ProviderDiscoveryState.Loading
    )
    val providerDiscoveryState: StateFlow<ProviderDiscoveryState> = _providerDiscoveryState

    val providerAccountSummaries: StateFlow<List<ProviderAccountSummary>> =
        subscriptionProviderRepository.observeAccountSummaries()
            .catch { emit(emptyList()) }
            .stateIn(
                scope = viewModelScope,
                initialValue = emptyList(),
                started = SharingStarted.WhileSubscribed(5_000L),
            )

    private val _providerSubscriptionForm = MutableStateFlow<ProviderSubscriptionForm?>(null)
    val providerSubscriptionForm: StateFlow<ProviderSubscriptionForm?> = _providerSubscriptionForm

    private val _providerOperationState = MutableStateFlow(ProviderOperationState())
    val providerOperationState: StateFlow<ProviderOperationState> = _providerOperationState

    init {
        refreshCodecPack()
        refreshExtensionPlugins()
        viewModelScope.launch {
            settings.flowOf(PreferencesKeys.EXTERNAL_EXTENSIONS).collect { enabled ->
                if (!enabled) closeExtensionSettings()
            }
        }
    }

    fun refreshSubscriptionProviders() {
        if (_providerOperationState.value.isBusy) return
        if (providerDiscoveryJob?.isActive == true) return
        providerDiscoveryJob = viewModelScope.launch {
            try {
                loadSubscriptionProviders()
            } finally {
                providerDiscoveryJob = null
            }
        }
    }

    fun selectSubscriptionProvider(providerId: String) {
        if (_providerOperationState.value.isBusy) return
        val descriptor = currentSubscriptionProviders().firstOrNull { provider ->
            provider.descriptor.providerId.value == providerId
        }?.descriptor ?: return
        val current = _providerSubscriptionForm.value
        if (
            current?.providerId == descriptor.providerId &&
            current.schemaVersion == descriptor.settingsSchema?.version &&
            descriptor.variants.any { variant -> variant.kind == current.providerKind }
        ) {
            return
        }
        val kind = descriptor.variants.first().kind
        _providerSubscriptionForm.value = ProviderSubscriptionForm.create(descriptor, kind)
    }

    fun selectSubscriptionProviderKind(kindValue: String) {
        if (_providerOperationState.value.isBusy) return
        val current = _providerSubscriptionForm.value ?: return
        val descriptor = currentSubscriptionProviders().firstOrNull { provider ->
            provider.descriptor.providerId == current.providerId
        }?.descriptor ?: return
        val kind = descriptor.variants.firstOrNull { variant ->
            variant.kind.value == kindValue
        }?.kind ?: return
        if (kind == current.providerKind) return
        _providerSubscriptionForm.value = ProviderSubscriptionForm.create(descriptor, kind)
    }

    fun updateSubscriptionProviderSetting(fieldKey: String, value: String?) {
        if (_providerOperationState.value.isBusy) return
        _providerSubscriptionForm.value = _providerSubscriptionForm.value?.update(fieldKey, value)
    }

    private fun synchronizeProviderSubscriptionForm(
        providers: List<DiscoveredSubscriptionProvider>,
    ) {
        val current = _providerSubscriptionForm.value
        val descriptor = providers.firstOrNull { provider ->
            provider.descriptor.providerId == current?.providerId
        }?.descriptor ?: providers.firstOrNull()?.descriptor
        if (descriptor == null) {
            _providerSubscriptionForm.value = null
            return
        }
        val kind = descriptor.variants.firstOrNull { variant ->
            variant.kind == current?.providerKind
        }?.kind ?: descriptor.variants.first().kind
        val currentDefinitions = current?.fields?.map(ProviderSubscriptionFormField::definition)
        if (
            current?.providerId != descriptor.providerId ||
            current.providerKind != kind ||
            current.schemaVersion != descriptor.settingsSchema?.version ||
            currentDefinitions != descriptor.settingsSchema?.fields.orEmpty()
        ) {
            _providerSubscriptionForm.value = ProviderSubscriptionForm.create(descriptor, kind)
        }
    }

    private suspend fun loadSubscriptionProviders(): List<DiscoveredSubscriptionProvider>? {
        val previousState = _providerDiscoveryState.value
        _providerDiscoveryState.value = ProviderDiscoveryState.Loading
        return try {
            val providers = withContext(Dispatchers.IO) {
                subscriptionProviderRepository.discoverProviders()
            }
            synchronizeProviderSubscriptionForm(providers)
            _providerDiscoveryState.value = providers.toProviderDiscoveryState()
            providers
        } catch (cancelled: CancellationException) {
            if (_providerDiscoveryState.value is ProviderDiscoveryState.Loading) {
                _providerDiscoveryState.value = previousState
            }
            throw cancelled
        } catch (error: Exception) {
            _providerDiscoveryState.value = ProviderDiscoveryState.Failed(
                failureCount = (error as? ProviderDiscoveryException)?.failureCount,
            )
            null
        }
    }

    private fun currentSubscriptionProviders(): List<DiscoveredSubscriptionProvider> =
        (_providerDiscoveryState.value as? ProviderDiscoveryState.Ready)?.providers.orEmpty()

    fun reauthenticateProviderAccount(playlistUrl: String) {
        if (_providerOperationState.value.isBusy) return
        val account = providerAccountSummaries.value.firstOrNull { summary ->
            summary.playlistUrl == playlistUrl && summary.requiresReauthentication
        } ?: return
        providerReauthenticationJob?.cancel()
        _providerOperationState.value = _providerOperationState.value.copy(
            preparingReauthenticationPlaylistUrl = playlistUrl,
        )
        providerReauthenticationJob = viewModelScope.launch {
            try {
                providerDiscoveryJob?.join()
                var provider = currentSubscriptionProviders().providerFor(account)
                if (provider == null) {
                    provider = loadSubscriptionProviders().orEmpty().providerFor(account)
                }
                if (provider == null) {
                    messager.emit(SettingMessage.ProviderSubscriptionFailed)
                    return@launch
                }
                resetAllInputs()
                properties.selectedState.value = DataSource.Provider
                properties.titleState.value = account.playlistTitle
                _providerSubscriptionForm.value =
                    ProviderSubscriptionForm.createForReauthentication(
                        descriptor = provider,
                        account = account,
                    )
            } finally {
                if (
                    _providerOperationState.value.preparingReauthenticationPlaylistUrl ==
                    playlistUrl
                ) {
                    _providerOperationState.value = _providerOperationState.value.copy(
                        preparingReauthenticationPlaylistUrl = null,
                    )
                }
            }
        }
    }

    fun refreshExtensionPlugins() {
        viewModelScope.launch(Dispatchers.IO) {
            _extensionPlugins.value = extensionPluginRepository.installedPlugins()
            refreshSubscriptionProviders()
        }
    }

    fun enableExtensionPlugin(
        packageName: String,
        serviceName: String,
        authorizationToken: PluginAuthorizationToken,
    ) {
        viewModelScope.launch {
            when (
                val result = extensionPluginRepository.enable(
                    packageName,
                    serviceName,
                    authorizationToken,
                )
            ) {
                is PluginEnableResult.Enabled -> Unit
                is PluginEnableResult.Rejected ->
                    messager.emit(SettingMessage.ExtensionOperationFailed)
            }
            refreshExtensionPlugins()
        }
    }

    fun reauthorizeExtensionPlugin(
        packageName: String,
        serviceName: String,
        authorizationToken: PluginAuthorizationToken,
    ) {
        viewModelScope.launch {
            when (
                val result = extensionPluginRepository.reauthorize(
                    packageName,
                    serviceName,
                    authorizationToken,
                )
            ) {
                is PluginEnableResult.Enabled -> Unit
                is PluginEnableResult.Rejected ->
                    messager.emit(SettingMessage.ExtensionOperationFailed)
            }
            refreshExtensionPlugins()
        }
    }

    fun disableExtensionPlugin(extensionId: String) {
        closeExtensionSettingsIfActive(extensionId)
        viewModelScope.launch {
            extensionPluginRepository.disable(extensionId)
            refreshExtensionPlugins()
        }
    }

    fun revokeExtensionPlugin(
        packageName: String,
        serviceName: String,
        extensionId: String?,
    ) {
        if (extensionId == null) {
            viewModelScope.launch { messager.emit(SettingMessage.ExtensionOperationFailed) }
            return
        }
        closeExtensionSettingsIfActive(extensionId)
        val operation: suspend () -> Unit = {
            extensionPluginRepository.revoke(packageName, serviceName)
            refreshExtensionPlugins()
        }
        extensionSettingsOperationQueue.launchDestructive(
            extensionId = extensionId,
            operation = operation,
        )
    }

    fun openExtensionSettings(extensionId: String, localeTag: String?) {
        val requestedExtensionId = ExtensionId(extensionId)
        val generation = ++extensionSettingsGeneration
        extensionSettingsLoadJob?.cancel()
        extensionSettingsRequestedId = requestedExtensionId
        _extensionSettings.value = null
        extensionSettingsLoadJob = extensionSettingsOperationQueue.launchOperation(extensionId) {
            val configuration = withContext(Dispatchers.IO) {
                extensionSettingsRepository.configuration(
                    requestedExtensionId,
                    localeTag,
                    PHONE_SETTINGS_SURFACE,
                )
            }
            if (generation == extensionSettingsGeneration) {
                _extensionSettings.value = configuration
            }
        }
    }

    fun closeExtensionSettings() {
        extensionSettingsGeneration++
        extensionSettingsLoadJob?.cancel()
        extensionSettingsLoadJob = null
        extensionSettingsRequestedId = null
        _extensionSettings.value = null
    }

    private fun closeExtensionSettingsIfActive(extensionId: String) {
        if (
            _extensionSettings.value?.extensionId?.value == extensionId ||
            extensionSettingsRequestedId?.value == extensionId
        ) {
            closeExtensionSettings()
        }
    }

    fun clearExtensionData(
        packageName: String,
        serviceName: String,
        extensionId: String?,
    ) {
        if (extensionId == null) {
            viewModelScope.launch { messager.emit(SettingMessage.ExtensionOperationFailed) }
            return
        }
        closeExtensionSettingsIfActive(extensionId)
        val operation: suspend () -> Unit = {
            when (
                val result = withContext(Dispatchers.IO) {
                    extensionPluginRepository.clearData(packageName, serviceName)
                }
            ) {
                is PluginDataClearResult.Cleared -> {
                    messager.emit(SettingMessage.ExtensionDataCleared)
                }
                is PluginDataClearResult.Rejected ->
                    messager.emit(SettingMessage.ExtensionOperationFailed)
            }
        }
        extensionSettingsOperationQueue.launchDestructive(
            extensionId = extensionId,
            operation = operation,
        )
    }

    fun exportExtensionDiagnostics(extensionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            extensionPluginRepository.diagnostics(extensionId)?.let { payload ->
                _extensionDiagnostics.emit(payload)
            }
        }
    }

    fun updateExtensionSetting(
        sectionId: String,
        fieldKey: String,
        editToken: ExtensionSettingEditToken,
        rawValue: String?,
        localeTag: String?,
    ) {
        val extensionId = _extensionSettings.value?.extensionId ?: return
        val generation = extensionSettingsGeneration
        val updateGeneration = ++extensionSettingsUpdateGeneration
        extensionSettingsOperationQueue.launchUpdate(extensionId.value) update@{
            val result = withContext(Dispatchers.IO) {
                when (
                    val update = extensionSettingsRepository.update(
                        extensionId,
                        sectionId,
                        fieldKey,
                        editToken,
                        rawValue,
                    )
                ) {
                    is ExtensionSettingUpdateResult.Updated -> {
                        ExtensionSettingsRefreshResult.Updated(
                            extensionSettingsRepository.configuration(
                                extensionId,
                                localeTag,
                                PHONE_SETTINGS_SURFACE,
                            )
                        )
                    }
                    is ExtensionSettingUpdateResult.Rejected -> {
                        ExtensionSettingsRefreshResult.Rejected(
                            configuration = extensionSettingsRepository.configuration(
                                extensionId,
                                localeTag,
                                PHONE_SETTINGS_SURFACE,
                            )
                        )
                    }
                }
            }
            if (
                generation != extensionSettingsGeneration ||
                updateGeneration != extensionSettingsUpdateGeneration ||
                _extensionSettings.value?.extensionId != extensionId
            ) {
                return@update
            }
            when (result) {
                is ExtensionSettingsRefreshResult.Updated -> {
                    _extensionSettings.value = result.configuration
                }
                is ExtensionSettingsRefreshResult.Rejected -> {
                    _extensionSettings.value = result.configuration
                    messager.emit(SettingMessage.ExtensionOperationFailed)
                }
            }
        }
    }

    private sealed interface ExtensionSettingsRefreshResult {
        data class Updated(
            val configuration: ExtensionSettingsConfiguration?,
        ) : ExtensionSettingsRefreshResult

        data class Rejected(
            val configuration: ExtensionSettingsConfiguration?,
        ) : ExtensionSettingsRefreshResult
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

        if (forTv && selected.supportsRemoteTvSubscription()) {
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
        if (selected.isSubscriptionProvider() && _providerOperationState.value.isBusy) {
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

                DataSource.Emby, DataSource.Jellyfin -> {
                    if (title.isEmpty()) {
                        messager.emit(SettingMessage.EmptyTitle)
                        return
                    }
                    if (inputBasicUrl.isBlank()) {
                        messager.emit(SettingMessage.EmptyUrl)
                        return
                    }
                    if (username.isBlank()) {
                        messager.emit(SettingMessage.ProviderCredentialsRequired)
                        return
                    }
                    subscribeProvider(
                        title = title,
                        baseUrl = basicUrl,
                        username = username,
                        password = password,
                        providerKind = when (selected) {
                            DataSource.Emby -> EmbyCompatibleProviderKinds.Emby
                            DataSource.Jellyfin -> EmbyCompatibleProviderKinds.Jellyfin
                        },
                    )
                    return
                }

                DataSource.Provider -> {
                    if (title.isBlank()) {
                        messager.emit(SettingMessage.EmptyTitle)
                        return
                    }
                    if (_providerDiscoveryState.value !is ProviderDiscoveryState.Ready) return
                    val form = _providerSubscriptionForm.value ?: return
                    when (
                        val result = form.buildRequest(
                            title = title,
                            stageCredential = subscriptionProviderRepository::stageCredential,
                        )
                    ) {
                        is ProviderSubscriptionFormBuildResult.Invalid -> {
                            _providerSubscriptionForm.value = result.form
                            messager.emit(SettingMessage.ProviderCredentialsRequired)
                        }
                        is ProviderSubscriptionFormBuildResult.Ready -> {
                            enqueueProviderSubscription(
                                request = result.request,
                                reauthenticationPlaylistUrl = form.reauthenticationPlaylistUrl,
                            )
                        }
                    }
                    return
                }

                else -> return
            }
        resetAllInputs()
    }

    private fun subscribeProvider(
        title: String,
        baseUrl: String,
        username: String,
        password: String,
        providerKind: ProviderKind,
    ) {
        val provider = currentSubscriptionProviders().singleBuiltInProviderFor(providerKind)
        if (provider == null) {
            messager.emit(SettingMessage.ProviderSubscriptionFailed)
            return
        }
        val form = ProviderSubscriptionForm.create(provider, providerKind)
            .update(SubscriptionProviderSettingKeys.BaseUrl, baseUrl)
            .update(SubscriptionProviderSettingKeys.Username, username)
            .update(SubscriptionProviderSettingKeys.Password, password)
        when (
            val result = form.buildRequest(
                title = title,
                stageCredential = subscriptionProviderRepository::stageCredential,
            )
        ) {
            is ProviderSubscriptionFormBuildResult.Invalid -> {
                messager.emit(SettingMessage.ProviderCredentialsRequired)
            }
            is ProviderSubscriptionFormBuildResult.Ready -> {
                enqueueProviderSubscription(request = result.request)
            }
        }
    }

    private fun enqueueProviderSubscription(
        request: ProviderSubscriptionRequest,
        reauthenticationPlaylistUrl: String? = null,
    ) {
        if (
            providerSubscriptionJob?.isActive == true ||
            _providerOperationState.value.submission != null
        ) {
            return
        }
        val operation = ProviderSubmissionOperation(
            providerId = request.providerId,
            providerKind = request.providerKind,
            reauthenticationPlaylistUrl = reauthenticationPlaylistUrl,
        )
        val submittedInputs = providerInputSnapshot()
        _providerOperationState.value = _providerOperationState.value.copy(
            submission = operation,
        )
        providerSubscriptionJob = viewModelScope.launch {
            try {
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        subscriptionProviderRepository.subscribe(request)
                    }
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                }
                result.fold(
                    onSuccess = { subscription ->
                        messager.emit(SettingMessage.ProviderAdded(subscription.channelCount))
                        if (providerInputSnapshot() == submittedInputs) {
                            resetAllInputs()
                        }
                    },
                    onFailure = {
                        messager.emit(SettingMessage.ProviderSubscriptionFailed)
                    },
                )
            } finally {
                if (_providerOperationState.value.submission == operation) {
                    _providerOperationState.value = _providerOperationState.value.copy(
                        submission = null,
                    )
                }
                providerSubscriptionJob = null
            }
        }
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
        _providerSubscriptionForm.value = _providerSubscriptionForm.value?.let { form ->
            val descriptor = currentSubscriptionProviders().firstOrNull { provider ->
                provider.descriptor.providerId == form.providerId
            }?.descriptor ?: return@let null
            ProviderSubscriptionForm.create(descriptor, form.providerKind)
        }
    }

    private fun providerInputSnapshot(): ProviderInputSnapshot = with(properties) {
        ProviderInputSnapshot(
            selected = selectedState.value,
            title = titleState.value,
            url = urlState.value,
            uri = uriState.value,
            localStorage = localStorageState.value,
            forTv = forTvState.value,
            basicUrl = basicUrlState.value,
            username = usernameState.value,
            password = passwordState.value,
            epg = epgState.value,
            providerForm = _providerSubscriptionForm.value,
        )
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

    private companion object {
        const val PHONE_SETTINGS_SURFACE = "phone"
    }
}

private data class ProviderInputSnapshot(
    val selected: DataSource,
    val title: String,
    val url: String,
    val uri: Uri,
    val localStorage: Boolean,
    val forTv: Boolean,
    val basicUrl: String,
    val username: String,
    val password: String,
    val epg: String,
    val providerForm: ProviderSubscriptionForm?,
)

private fun DataSource.isSubscriptionProvider(): Boolean = when (this) {
    DataSource.Emby,
    DataSource.Jellyfin,
    DataSource.Provider -> true

    else -> false
}

private fun DataSource.supportsRemoteTvSubscription(): Boolean = when (this) {
    DataSource.M3U,
    DataSource.EPG,
    DataSource.Xtream -> true

    DataSource.Emby,
    DataSource.Jellyfin,
    DataSource.Provider,
    DataSource.Dropbox -> false
}

private fun List<DiscoveredSubscriptionProvider>.providerFor(
    account: ProviderAccountSummary,
) = singleOrNull { discovered ->
    discovered.descriptor.providerId == account.providerId &&
        discovered.descriptor.variants.any { variant -> variant.kind == account.providerKind }
}?.descriptor
