package com.m3u.business.setting

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import androidx.work.workDataOf
import com.m3u.core.foundation.architecture.Publisher
import com.m3u.core.foundation.architecture.preferences.PreferencesKeys
import com.m3u.core.foundation.architecture.preferences.Settings
import com.m3u.core.foundation.architecture.preferences.ThemePreference
import com.m3u.core.foundation.architecture.preferences.ThemePreset
import com.m3u.core.foundation.architecture.preferences.ThemeStyle
import com.m3u.core.foundation.architecture.preferences.applyThemePreference
import com.m3u.core.foundation.architecture.preferences.flowOf
import com.m3u.core.foundation.architecture.preferences.set
import com.m3u.core.foundation.util.basic.PlaylistInputKind
import com.m3u.core.foundation.util.basic.normalizePlaylistInputForSubmission
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
import com.m3u.data.repository.plugin.PluginAuthorizationToken
import com.m3u.data.repository.plugin.PluginDataClearResult
import com.m3u.data.repository.plugin.PluginEnableResult
import com.m3u.data.repository.tv.TvRepository
import com.m3u.data.service.Messager
import com.m3u.data.worker.BackupWorker
import com.m3u.data.worker.RestoreWorker
import com.m3u.data.worker.SubscriptionWorker
import com.m3u.data.worker.enqueuePersistedUriWork
import com.m3u.data.worker.persistedUriPermissionTag
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.ExtensionSettingKeys
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

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
    private val savedStateHandle: SavedStateHandle,
    publisher: Publisher,
    // FIXME: do not use dao in viewmodel
    private val colorSchemeDao: ColorSchemeDao,
) : ViewModel() {
    private val _codecPackState = MutableStateFlow(codecPackRepository.toPendingState())
    val codecPackState: StateFlow<CodecPackState> = _codecPackState
    private var providerSubscriptionJob: Job? = null
    private var providerDiscoveryJob: Job? = null
    private var providerDiscoveryGeneration = 0L
    private var providerLocaleTag: String? = null
    private var providerReauthenticationJob: Job? = null
    private val subscriptionDraftSession = SubscriptionDraftSession()
    private var extensionSettingsLoadJob: Job? = null
    private var extensionSettingsRequestedId: ExtensionId? = null
    private var extensionSettingsGeneration = 0L
    private var extensionSettingsUpdateGeneration = 0L
    private val extensionSettingsOperationQueue = ExtensionSettingsOperationQueue(
        scope = viewModelScope,
        onFailure = { messager.emit(SettingMessage.ExtensionOperationFailed) },
    )
    private val extensionSettingUpdateGate = ExtensionSettingUpdateGate()
    private val extensionPluginOperationController = ExtensionPluginOperationController()

    private val _extensionPluginDiscoveryState =
        MutableStateFlow<ExtensionPluginDiscoveryState>(
            ExtensionPluginDiscoveryState.Loading()
        )
    val extensionPluginDiscoveryState: StateFlow<ExtensionPluginDiscoveryState> =
        _extensionPluginDiscoveryState

    private val _extensionSettingsState = MutableStateFlow<ExtensionSettingsState>(
        ExtensionSettingsState.Closed
    )
    val extensionSettingsState: StateFlow<ExtensionSettingsState> = _extensionSettingsState

    val extensionPluginOperationState: StateFlow<ExtensionPluginOperationState> =
        extensionPluginOperationController.state

    private val _extensionDiagnostics = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val extensionDiagnostics = _extensionDiagnostics.asSharedFlow()

    private val _subscriptionAccepted = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val subscriptionAccepted = _subscriptionAccepted.asSharedFlow()

    private val trackedPlaylistSubscription = combine(
        savedStateHandle.getStateFlow(PLAYLIST_SUBSCRIPTION_TITLE_KEY, ""),
        savedStateHandle.getStateFlow(PLAYLIST_SUBSCRIPTION_SOURCE_KEY, ""),
        savedStateHandle.getStateFlow(PLAYLIST_SUBSCRIPTION_WORK_ID_KEY, ""),
    ) { title, sourceValue, workIdValue ->
        restorePlaylistSubscriptionTracking(
            title = title,
            sourceValue = sourceValue,
            workIdValue = workIdValue,
        )
    }

    val playlistSubscriptionState: StateFlow<PlaylistSubscriptionState> =
        trackedPlaylistSubscription
            .flatMapLatest { tracking ->
                if (tracking == null) {
                    flowOf(PlaylistSubscriptionState.Idle)
                } else {
                    workManager.getWorkInfoByIdFlow(tracking.workId)
                        .map { workInfo ->
                            resolvePlaylistSubscriptionState(
                                tracking = tracking,
                                workState = workInfo?.state,
                            )
                        }
                        .catch { error ->
                            if (error is CancellationException) throw error
                            emit(
                                resolvePlaylistSubscriptionState(
                                    tracking = tracking,
                                    workState = WorkInfo.State.FAILED,
                                )
                            )
                        }
                }
            }
            .distinctUntilChanged()
            .stateIn(
                scope = viewModelScope,
                initialValue = currentPlaylistSubscriptionTracking()
                    ?.let { tracking ->
                        resolvePlaylistSubscriptionState(
                            tracking = tracking,
                            workState = null,
                        )
                    }
                    ?: PlaylistSubscriptionState.Idle,
                started = SharingStarted.Eagerly,
            )

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
        viewModelScope.launch {
            settings.flowOf(PreferencesKeys.EXTERNAL_EXTENSIONS).collect { enabled ->
                if (!enabled) closeExtensionSettings()
                requestExtensionPluginRefresh(queueIfBusy = true)
            }
        }
    }

    fun refreshSubscriptionProviders() {
        if (_providerOperationState.value.isBusy) return
        startSubscriptionProviderDiscovery(providerLocaleTag)
    }

    fun refreshSubscriptionProvidersForLocale(localeTag: String) {
        val requestedLocaleTag = localeTag.trim().takeIf(String::isNotEmpty)
        if (providerLocaleTag == requestedLocaleTag) return
        providerLocaleTag = requestedLocaleTag
        startSubscriptionProviderDiscovery(requestedLocaleTag)
        extensionSettingsRequestedId?.value?.let { extensionId ->
            openExtensionSettings(extensionId, requestedLocaleTag)
        }
    }

    private fun startSubscriptionProviderDiscovery(localeTag: String?): Job {
        val previousJob = providerDiscoveryJob
        val generation = ++providerDiscoveryGeneration
        return viewModelScope.launch {
            previousJob?.cancelAndJoin()
            try {
                loadSubscriptionProviders(localeTag)
            } finally {
                if (providerDiscoveryGeneration == generation) {
                    providerDiscoveryJob = null
                }
            }
        }.also { job -> providerDiscoveryJob = job }
    }

    private suspend fun awaitLatestSubscriptionProviderDiscovery(
        forceRefresh: Boolean,
    ) {
        var awaitedJob = if (forceRefresh) {
            startSubscriptionProviderDiscovery(providerLocaleTag)
        } else {
            providerDiscoveryJob ?: startSubscriptionProviderDiscovery(providerLocaleTag)
        }
        while (true) {
            awaitedJob.join()
            val latestJob = providerDiscoveryJob
            if (latestJob == null || latestJob === awaitedJob) return
            awaitedJob = latestJob
        }
    }

    fun selectSubscriptionProviderVariant(
        providerId: String,
        kindValue: String,
    ) {
        if (_providerOperationState.value.isBusy) return
        val descriptor = currentSubscriptionProviders().firstOrNull { provider ->
            provider.descriptor.providerId.value == providerId
        }?.descriptor ?: return
        val kind = descriptor.variants.firstOrNull { variant ->
            variant.kind.value == kindValue && variant.userSelectable
        }?.kind ?: return
        val current = _providerSubscriptionForm.value
        if (current.matchesNewSubscription(descriptor, kind)) {
            return
        }
        _providerSubscriptionForm.value = ProviderSubscriptionForm.create(descriptor, kind)
    }

    fun beginSubscriptionDraft(
        draftKey: String,
        source: DataSource,
    ) {
        if (source !in SUBSCRIPTION_DRAFT_SOURCES) return
        if (!subscriptionDraftSession.begin(draftKey)) return
        resetAllInputs()
        properties.selectedState.value = source
    }

    fun updateSubscriptionProviderSetting(fieldKey: String, value: String?) {
        if (_providerOperationState.value.isBusy) return
        _providerSubscriptionForm.value = _providerSubscriptionForm.value?.update(fieldKey, value)
    }

    private fun synchronizeProviderSubscriptionForm(
        providers: List<DiscoveredSubscriptionProvider>,
    ) {
        _providerSubscriptionForm.value = providers.reconcileSubscriptionForm(
            current = _providerSubscriptionForm.value,
        )
    }

    private suspend fun loadSubscriptionProviders(
        localeTag: String? = providerLocaleTag,
    ): List<DiscoveredSubscriptionProvider>? {
        val previousState = _providerDiscoveryState.value
        _providerDiscoveryState.value = ProviderDiscoveryState.Loading
        return try {
            val providers = withContext(Dispatchers.IO) {
                subscriptionProviderRepository.discoverProviders(localeTag)
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
                awaitLatestSubscriptionProviderDiscovery(forceRefresh = false)
                var provider = currentSubscriptionProviders().providerFor(account)
                if (provider == null) {
                    awaitLatestSubscriptionProviderDiscovery(forceRefresh = true)
                    provider = currentSubscriptionProviders().providerFor(account)
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
        requestExtensionPluginRefresh(queueIfBusy = false)
    }

    private fun requestExtensionPluginRefresh(queueIfBusy: Boolean) {
        launchExtensionPluginOperation(
            operation = ExtensionPluginOperation.Refresh,
            queueRefreshIfBusy = queueIfBusy,
        ) {
            refreshExtensionPluginsInternal()
        }
    }

    fun enableExtensionPlugin(
        packageName: String,
        serviceName: String,
        authorizationToken: PluginAuthorizationToken,
    ) {
        launchExtensionPluginOperation(
            ExtensionPluginOperation.Enable(packageName, serviceName)
        ) {
            val enabled = when (
                val result = extensionPluginRepository.enable(
                    packageName,
                    serviceName,
                    authorizationToken,
                )
            ) {
                is PluginEnableResult.Enabled -> true
                is PluginEnableResult.Rejected -> false
            }
            refreshExtensionPluginsInternal()
            if (!enabled) {
                messager.emit(SettingMessage.ExtensionOperationFailed)
            }
        }
    }

    fun reauthorizeExtensionPlugin(
        packageName: String,
        serviceName: String,
        authorizationToken: PluginAuthorizationToken,
    ) {
        launchExtensionPluginOperation(
            ExtensionPluginOperation.Reauthorize(packageName, serviceName)
        ) {
            val reauthorized = when (
                val result = extensionPluginRepository.reauthorize(
                    packageName,
                    serviceName,
                    authorizationToken,
                )
            ) {
                is PluginEnableResult.Enabled -> true
                is PluginEnableResult.Rejected -> false
            }
            refreshExtensionPluginsInternal()
            if (!reauthorized) {
                messager.emit(SettingMessage.ExtensionOperationFailed)
            }
        }
    }

    fun disableExtensionPlugin(extensionId: String) {
        launchExtensionPluginOperation(
            operation = ExtensionPluginOperation.Disable(extensionId),
            onStarted = { closeExtensionSettingsIfActive(extensionId) },
        ) {
            val disabled = extensionPluginRepository.disable(extensionId)
            refreshExtensionPluginsInternal()
            if (!disabled) {
                messager.emit(SettingMessage.ExtensionOperationFailed)
            }
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
        launchDestructiveExtensionPluginOperation(
            operation = ExtensionPluginOperation.Revoke(
                packageName = packageName,
                serviceName = serviceName,
                extensionId = extensionId,
            ),
            extensionId = extensionId,
            onStarted = { closeExtensionSettingsIfActive(extensionId) },
        ) {
            extensionPluginRepository.revoke(packageName, serviceName)
            refreshExtensionPluginsInternal()
        }
    }

    fun openExtensionSettings(extensionId: String, localeTag: String?) {
        val requestedExtensionId = ExtensionId(extensionId)
        val generation = ++extensionSettingsGeneration
        extensionSettingsLoadJob?.cancel()
        extensionSettingsRequestedId?.value?.let(extensionSettingUpdateGate::clear)
        extensionSettingsRequestedId = requestedExtensionId
        _extensionSettingsState.value =
            ExtensionSettingsState.Loading(requestedExtensionId)
        extensionSettingsLoadJob = extensionSettingsOperationQueue.launchOperation(extensionId) {
            try {
                val configuration = withContext(Dispatchers.IO) {
                    extensionSettingsRepository.configuration(
                        requestedExtensionId,
                        localeTag,
                        PHONE_SETTINGS_SURFACE,
                    )
                }
                if (generation == extensionSettingsGeneration) {
                    _extensionSettingsState.value =
                        configuration.toExtensionSettingsState(requestedExtensionId)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (generation == extensionSettingsGeneration) {
                    _extensionSettingsState.value =
                        ExtensionSettingsState.Error(requestedExtensionId)
                }
                throw failure
            }
        }
    }

    fun closeExtensionSettings() {
        extensionSettingsGeneration++
        extensionSettingsLoadJob?.cancel()
        extensionSettingsLoadJob = null
        extensionSettingsRequestedId?.value?.let(extensionSettingUpdateGate::clear)
        extensionSettingsRequestedId = null
        _extensionSettingsState.value = ExtensionSettingsState.Closed
    }

    private fun closeExtensionSettingsIfActive(extensionId: String) {
        if (
            _extensionSettingsState.value.extensionId?.value == extensionId ||
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
        launchDestructiveExtensionPluginOperation(
            operation = ExtensionPluginOperation.ClearData(
                packageName = packageName,
                serviceName = serviceName,
                extensionId = extensionId,
            ),
            extensionId = extensionId,
            onStarted = { closeExtensionSettingsIfActive(extensionId) },
        ) {
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
        val content =
            _extensionSettingsState.value as? ExtensionSettingsState.Content
                ?: return
        val configuration = content.configuration
        val extensionId = configuration.extensionId
        val qualifiedKey = runCatching {
            ExtensionSettingKeys.qualified(sectionId, fieldKey)
        }.getOrNull() ?: return
        if (!extensionSettingUpdateGate.tryStart(extensionId.value, qualifiedKey)) return
        val refreshPluginProjection =
            configuration.networkOriginState(sectionId, fieldKey) != null
        _extensionSettingsState.value = content.copy(
            updatingKeys = content.updatingKeys + qualifiedKey,
        )
        val generation = extensionSettingsGeneration
        val updateGeneration = ++extensionSettingsUpdateGeneration
        extensionSettingsOperationQueue.launchUpdate(extensionId.value) update@{
            try {
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
                                configuration = extensionSettingsRepository.configuration(
                                    extensionId,
                                    localeTag,
                                    PHONE_SETTINGS_SURFACE,
                                ),
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
                    result is ExtensionSettingsRefreshResult.Updated &&
                    refreshPluginProjection
                ) {
                    requestExtensionPluginRefresh(queueIfBusy = true)
                }
                if (
                    generation != extensionSettingsGeneration ||
                    updateGeneration != extensionSettingsUpdateGeneration ||
                    _extensionSettingsState.value.extensionId != extensionId
                ) {
                    return@update
                }
                val updatingKeys = currentExtensionSettingUpdateKeys(extensionId) - qualifiedKey
                when (result) {
                    is ExtensionSettingsRefreshResult.Updated -> {
                        _extensionSettingsState.value =
                            result.configuration.toExtensionSettingsState(
                                extensionId = extensionId,
                                updatingKeys = updatingKeys,
                            )
                    }
                    is ExtensionSettingsRefreshResult.Rejected -> {
                        _extensionSettingsState.value =
                            result.configuration.toExtensionSettingsState(
                                extensionId = extensionId,
                                updatingKeys = updatingKeys,
                            )
                        messager.emit(SettingMessage.ExtensionOperationFailed)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (
                    generation == extensionSettingsGeneration &&
                    _extensionSettingsState.value.extensionId == extensionId
                ) {
                    _extensionSettingsState.value =
                        ExtensionSettingsState.Error(extensionId)
                }
                throw failure
            } finally {
                extensionSettingUpdateGate.finish(extensionId.value, qualifiedKey)
                val current = _extensionSettingsState.value
                if (
                    current is ExtensionSettingsState.Content &&
                    current.extensionId == extensionId &&
                    qualifiedKey in current.updatingKeys
                ) {
                    _extensionSettingsState.value = current.copy(
                        updatingKeys = current.updatingKeys - qualifiedKey,
                    )
                }
            }
        }
    }

    private fun currentExtensionSettingUpdateKeys(extensionId: ExtensionId): Set<String> =
        (_extensionSettingsState.value as? ExtensionSettingsState.Content)
            ?.takeIf { content -> content.extensionId == extensionId }
            ?.updatingKeys
            .orEmpty()

    private fun launchExtensionPluginOperation(
        operation: ExtensionPluginOperation,
        queueRefreshIfBusy: Boolean = false,
        onStarted: () -> Unit = {},
        block: suspend () -> Unit,
    ): Job? {
        val running = extensionPluginOperationController.tryStart(
            operation = operation,
            queueRefreshIfBusy = queueRefreshIfBusy,
        ) ?: return null
        onStarted()
        return viewModelScope.launch {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                messager.emit(SettingMessage.ExtensionOperationFailed)
            } finally {
                if (
                    extensionPluginOperationController
                        .finishAndConsumePendingRefresh(running)
                ) {
                    requestExtensionPluginRefresh(queueIfBusy = false)
                }
            }
        }
    }

    private fun launchDestructiveExtensionPluginOperation(
        operation: ExtensionPluginOperation,
        extensionId: String,
        onStarted: () -> Unit,
        block: suspend () -> Unit,
    ): Job? {
        val running = extensionPluginOperationController.tryStart(operation) ?: return null
        onStarted()
        return extensionSettingsOperationQueue.launchDestructive(
            extensionId = extensionId,
            operation = block,
        ).also { job ->
            job.invokeOnCompletion {
                if (
                    extensionPluginOperationController
                        .finishAndConsumePendingRefresh(running)
                ) {
                    requestExtensionPluginRefresh(queueIfBusy = false)
                }
            }
        }
    }

    private suspend fun refreshExtensionPluginsInternal() {
        val previousState = _extensionPluginDiscoveryState.value
        val previousPlugins = previousState.plugins
        _extensionPluginDiscoveryState.value =
            ExtensionPluginDiscoveryState.Loading(previousPlugins)
        try {
            val plugins = withContext(Dispatchers.IO) {
                extensionPluginRepository.installedPlugins()
            }
            _extensionPluginDiscoveryState.value =
                plugins.toExtensionPluginDiscoveryState()
            refreshSubscriptionProviders()
        } catch (cancelled: CancellationException) {
            if (_extensionPluginDiscoveryState.value is ExtensionPluginDiscoveryState.Loading) {
                _extensionPluginDiscoveryState.value = previousState
            }
            throw cancelled
        } catch (failure: Exception) {
            _extensionPluginDiscoveryState.value =
                ExtensionPluginDiscoveryState.Error(previousPlugins)
            throw failure
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

    val playlists: StateFlow<Map<Playlist, Int>?> = playlistRepository
        .observeAllCounts()
        .map<Map<Playlist, Int>, Map<Playlist, Int>?> { counts -> counts }
        .stateIn(
            scope = viewModelScope,
            initialValue = null,
            started = SharingStarted.WhileSubscribed(5_000L),
        )

    val playlistSubscriptionInProgress: StateFlow<Boolean> = workManager
        .getWorkInfosFlow(
            WorkQuery.fromStates(
                WorkInfo.State.RUNNING,
                WorkInfo.State.ENQUEUED,
            )
        )
        .map { infos ->
            infos.any { info ->
                SubscriptionWorker.TAG in info.tags &&
                    DataSource.EPG.value !in info.tags
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            initialValue = false,
            started = SharingStarted.WhileSubscribed(5_000L),
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

    val colorSchemes: StateFlow<List<ColorScheme>> = colorSchemeDao
        .observeAll()
        .catch { emit(emptyList()) }
        .map { stored ->
            stored.filterNot(ColorScheme::isLegacyWarmPresetRecord)
        }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun onClipboard(url: String) {
        val source = properties.selectedState.value
        val input = ClipboardPlaylistInput.parse(
            rawUrl = url,
            source = source,
        )
        properties.titleState.value = input.title
        properties.urlState.value = input.m3uUrl.orEmpty()
        properties.basicUrlState.value = ""
        properties.usernameState.value = ""
        properties.passwordState.value = ""
        properties.xtreamPlaylistTypeState.value = null
        when (source) {
            DataSource.M3U -> Unit
            DataSource.Xtream -> {
                val xtreamInput = input.xtreamInput ?: return
                properties.basicUrlState.value = xtreamInput.basicUrl
                properties.usernameState.value = xtreamInput.username
                properties.passwordState.value = xtreamInput.password
                properties.xtreamPlaylistTypeState.value = xtreamInput.type
                    ?.takeIf { type -> type in SUPPORTED_XTREAM_PLAYLIST_TYPES }
            }

            else -> Unit
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
        val title = properties.titleState.value.normalizePlaylistInputForSubmission(
            PlaylistInputKind.TITLE
        )
        val url = properties.urlState.value.normalizePlaylistInputForSubmission(
            PlaylistInputKind.URL
        )
        val uri = properties.uriState.value
        val inputBasicUrl = properties.basicUrlState.value.normalizePlaylistInputForSubmission(
            PlaylistInputKind.BASE_URL
        )
        val username = properties.usernameState.value.normalizePlaylistInputForSubmission(
            PlaylistInputKind.USERNAME
        )
        val password = properties.passwordState.value.normalizePlaylistInputForSubmission(
            PlaylistInputKind.PASSWORD
        )
        val epg = properties.epgState.value.normalizePlaylistInputForSubmission(
            PlaylistInputKind.EPG_URL
        )
        val xtreamPlaylistType = properties.xtreamPlaylistTypeState.value
            ?.takeIf { type -> type in SUPPORTED_XTREAM_PLAYLIST_TYPES }
        val selected = properties.selectedState.value
        val localStorage = properties.localStorageState.value
        val forTv = properties.forTvState.value
        properties.titleState.value = title
        properties.urlState.value = url
        properties.basicUrlState.value = inputBasicUrl
        properties.usernameState.value = username
        properties.passwordState.value = password
        properties.epgState.value = epg
        properties.xtreamPlaylistTypeState.value = xtreamPlaylistType

        val xtreamPlaylistUrl = buildXtreamPlaylistUrlOrEmpty(
            basicUrl = inputBasicUrl,
            username = username,
            password = password,
            type = xtreamPlaylistType,
        )

        val localUriReference = uri
            .takeIf { uri != Uri.EMPTY }?.toString().orEmpty()
        val normalizedLocalUriReference = localUriReference
            .normalizePlaylistInputForSubmission(PlaylistInputKind.URL)
        val localUriReferenceIsValid =
            localUriReference.isNotBlank() &&
                localUriReference == normalizedLocalUriReference
        val m3uUrlOrUri = normalizedLocalUriReference
            .takeIf { localStorage }
            ?: url
        val submittedUrl = when (selected) {
            DataSource.M3U -> m3uUrlOrUri
            DataSource.Xtream -> xtreamPlaylistUrl
            else -> ""
        }

        val basicUrl = if (inputBasicUrl.startWithHttpScheme()) inputBasicUrl
        else "http://$inputBasicUrl"

        if (
            currentPlaylistSubscriptionTracking() != null ||
            playlistSubscriptionInProgress.value
        ) {
            return
        }

        if (forTv && selected.supportsRemoteTvSubscription()) {
            subscribeForTv(
                selected = selected,
                title = title,
                url = submittedUrl,
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
                    if (title.isBlank()) {
                        messager.emit(SettingMessage.EmptyTitle)
                        return
                    }
                    if (localStorage) {
                        if (uri == Uri.EMPTY) {
                            messager.emit(SettingMessage.EmptyFile)
                            return
                        }
                        if (!localUriReferenceIsValid) {
                            messager.emit(SettingMessage.FileAccessFailed)
                            return
                        }
                    } else {
                        if (url.isBlank()) {
                            messager.emit(SettingMessage.EmptyUrl)
                            return
                        }
                    }
                    val workId = runCatching {
                        SubscriptionWorker.m3u(
                            workManager = workManager,
                            title = title,
                            url = m3uUrlOrUri,
                        )
                    }.getOrElse {
                        messager.emit(SettingMessage.PlaylistOperationFailed)
                        return
                    }
                    rememberPlaylistSubscription(
                        title = title,
                        source = DataSource.M3U,
                        workId = workId,
                    )
                    messager.emit(SettingMessage.Enqueued)
                    _subscriptionAccepted.tryEmit(Unit)
                }

                DataSource.EPG -> {
                    if (title.isBlank()) {
                        messager.emit(SettingMessage.EmptyEpgTitle)
                        return
                    }
                    if (epg.isBlank()) {
                        messager.emit(SettingMessage.EmptyEpg)
                        return
                    }
                    viewModelScope.launch {
                        runCatching {
                            playlistRepository.insertEpgAsPlaylist(title, epg)
                        }.fold(
                            onSuccess = {
                                messager.emit(SettingMessage.EpgAdded)
                                _subscriptionAccepted.emit(Unit)
                            },
                            onFailure = { error ->
                                if (error is CancellationException) throw error
                                messager.emit(SettingMessage.PlaylistOperationFailed)
                            },
                        )
                    }
                }

                DataSource.Xtream -> {
                    if (title.isBlank()) {
                        messager.emit(SettingMessage.EmptyTitle)
                        return
                    }
                    if (inputBasicUrl.isBlank()) {
                        messager.emit(SettingMessage.EmptyUrl)
                        return
                    }
                    if (username.isBlank() || password.isBlank()) {
                        return
                    }
                    val workId = runCatching {
                        SubscriptionWorker.xtream(
                            workManager = workManager,
                            title = title,
                            url = xtreamPlaylistUrl,
                            basicUrl = basicUrl,
                            username = username,
                            password = password,
                        )
                    }.getOrElse {
                        messager.emit(SettingMessage.PlaylistOperationFailed)
                        return
                    }
                    rememberPlaylistSubscription(
                        title = title,
                        source = DataSource.Xtream,
                        workId = workId,
                    )
                    messager.emit(SettingMessage.Enqueued)
                    _subscriptionAccepted.tryEmit(Unit)
                }

                DataSource.Provider -> {
                    if (title.isBlank()) {
                        messager.emit(SettingMessage.EmptyTitle)
                        return
                    }
                    val form = _providerSubscriptionForm.value ?: return
                    if (!_providerDiscoveryState.value.supports(form)) return
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

    fun cancelPlaylistSubscription() {
        currentPlaylistSubscriptionTracking()
            ?.workId
            ?.let(workManager::cancelWorkById)
    }

    fun dismissPlaylistSubscriptionStatus() {
        if (!playlistSubscriptionState.value.isTerminal) return
        clearPlaylistSubscriptionTracking()
    }

    private fun rememberPlaylistSubscription(
        title: String,
        source: DataSource,
        workId: UUID,
    ) {
        val tracking = createPlaylistSubscriptionTracking(
            title = title,
            source = source,
            workId = workId,
        ) ?: return
        savedStateHandle[PLAYLIST_SUBSCRIPTION_WORK_ID_KEY] = ""
        savedStateHandle[PLAYLIST_SUBSCRIPTION_TITLE_KEY] = tracking.title
        savedStateHandle[PLAYLIST_SUBSCRIPTION_SOURCE_KEY] = tracking.source.value
        savedStateHandle[PLAYLIST_SUBSCRIPTION_WORK_ID_KEY] = tracking.workId.toString()
    }

    private fun currentPlaylistSubscriptionTracking(): PlaylistSubscriptionTracking? =
        restorePlaylistSubscriptionTracking(
            title = savedStateHandle[PLAYLIST_SUBSCRIPTION_TITLE_KEY] ?: "",
            sourceValue = savedStateHandle[PLAYLIST_SUBSCRIPTION_SOURCE_KEY] ?: "",
            workIdValue = savedStateHandle[PLAYLIST_SUBSCRIPTION_WORK_ID_KEY] ?: "",
        )

    private fun clearPlaylistSubscriptionTracking() {
        savedStateHandle[PLAYLIST_SUBSCRIPTION_WORK_ID_KEY] = ""
        savedStateHandle[PLAYLIST_SUBSCRIPTION_TITLE_KEY] = ""
        savedStateHandle[PLAYLIST_SUBSCRIPTION_SOURCE_KEY] = ""
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
                    onSuccess = {
                        messager.emit(SettingMessage.ProviderAdded)
                        if (providerInputSnapshot() == submittedInputs) {
                            resetAllInputs()
                        }
                        _subscriptionAccepted.emit(Unit)
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
                if (title.isBlank()) {
                    messager.emit(SettingMessage.EmptyTitle)
                    return
                }
                if (url.isBlank()) {
                    messager.emit(SettingMessage.EmptyUrl)
                    return
                }
            }

            DataSource.EPG -> {
                if (title.isBlank()) {
                    messager.emit(SettingMessage.EmptyEpgTitle)
                    return
                }
                if (epg.isBlank()) {
                    messager.emit(SettingMessage.EmptyEpg)
                    return
                }
            }

            DataSource.Xtream -> {
                if (title.isBlank()) {
                    messager.emit(SettingMessage.EmptyTitle)
                    return
                }
                if (basicUrl.removePrefix("http://").isBlank()) {
                    messager.emit(SettingMessage.EmptyUrl)
                    return
                }
                if (username.isBlank() || password.isBlank()) {
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
                _subscriptionAccepted.emit(Unit)
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
        val request = OneTimeWorkRequestBuilder<BackupWorker>()
            .setInputData(
                workDataOf(
                    BackupWorker.INPUT_URI to uri.toString()
                )
            )
            .addTag(BackupWorker.TAG)
            .addTag(persistedUriPermissionTag(uri))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        enqueuePersistedUriWork(
            workManager = workManager,
            permissionTag = persistedUriPermissionTag(uri),
        ) {
            workManager.enqueueUniqueWork(
                BackupWorker.UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
        messager.emit(SettingMessage.BackingUp)
    }

    fun restore(uri: Uri) {
        val request = OneTimeWorkRequestBuilder<RestoreWorker>()
            .setInputData(
                workDataOf(
                    RestoreWorker.INPUT_URI to uri.toString()
                )
            )
            .addTag(RestoreWorker.TAG)
            .addTag(persistedUriPermissionTag(uri))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        enqueuePersistedUriWork(
            workManager = workManager,
            permissionTag = persistedUriPermissionTag(uri),
        ) {
            workManager.enqueueUniqueWork(
                RestoreWorker.UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
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
            xtreamPlaylistTypeState.value = null
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
            xtreamPlaylistType = xtreamPlaylistTypeState.value,
            providerForm = _providerSubscriptionForm.value?.inputSnapshot(),
        )
    }

    fun deleteEpgPlaylist(epgUrl: String) {
        viewModelScope.launch {
            playlistRepository.deleteEpgPlaylistAndProgrammes(epgUrl)
        }
    }

    fun selectTheme(theme: ThemePreference) {
        viewModelScope.launch {
            settings.applyThemePreference(theme)
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun applyColor(
        prev: ColorScheme?,
        argb: Int,
        isDark: Boolean
    ) {
        viewModelScope.launch {
            colorSchemeDao.replace(
                previous = prev,
                replacement = ColorScheme(
                    argb = argb,
                    isDark = isDark,
                    name = "#${argb.toHexString(HexFormat.UpperCase)}"
                ),
            )
            settings.applyThemePreference(
                ThemePreference(
                    presetId = ThemePreset.MATERIAL,
                    argb = argb,
                    isDark = isDark,
                    style = ThemeStyle.MATERIAL,
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
        const val PLAYLIST_SUBSCRIPTION_TITLE_KEY = "playlist_subscription_title"
        const val PLAYLIST_SUBSCRIPTION_SOURCE_KEY = "playlist_subscription_source"
        const val PLAYLIST_SUBSCRIPTION_WORK_ID_KEY = "playlist_subscription_work_id"
        val SUBSCRIPTION_DRAFT_SOURCES = setOf(
            DataSource.M3U,
            DataSource.EPG,
            DataSource.Xtream,
            DataSource.Provider,
        )
        val SUPPORTED_XTREAM_PLAYLIST_TYPES = setOf(
            DataSource.Xtream.TYPE_LIVE,
            DataSource.Xtream.TYPE_SERIES,
            DataSource.Xtream.TYPE_VOD,
        )
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
    val xtreamPlaylistType: String?,
    val providerForm: ProviderFormInputSnapshot?,
)

private data class ProviderFormInputSnapshot(
    val providerId: ExtensionId,
    val providerKind: String,
    val schemaVersion: Int?,
    val reauthenticationPlaylistUrl: String?,
    val inputs: List<Pair<String, String?>>,
)

private fun ProviderSubscriptionForm.inputSnapshot(): ProviderFormInputSnapshot =
    ProviderFormInputSnapshot(
        providerId = providerId,
        providerKind = providerKind.value,
        schemaVersion = schemaVersion,
        reauthenticationPlaylistUrl = reauthenticationPlaylistUrl,
        inputs = fields
            .map { field -> field.definition.key to field.input }
            .sortedBy { (key, _) -> key },
    )

private fun DataSource.isSubscriptionProvider(): Boolean = when (this) {
    DataSource.Provider -> true

    else -> false
}

private fun DataSource.supportsRemoteTvSubscription(): Boolean = when (this) {
    DataSource.M3U,
    DataSource.EPG,
    DataSource.Xtream -> true

    else -> false
}

private val LEGACY_WARM_PRESET_NAMES = setOf("parchment", "ink")

private fun ColorScheme.isLegacyWarmPresetRecord(): Boolean =
    argb == ThemePreset.WARM_EDITORIAL_SEED &&
        name in LEGACY_WARM_PRESET_NAMES

internal fun buildXtreamPlaylistUrlOrEmpty(
    basicUrl: String,
    username: String,
    password: String,
    type: String?,
): String {
    if (type == null) return ""
    val resolvedBasicUrl = if (basicUrl.startWithHttpScheme()) {
        basicUrl
    } else {
        "http://$basicUrl"
    }
    val protocol = if (resolvedBasicUrl.startsWith("https://", ignoreCase = true)) {
        "https"
    } else {
        "http"
    }
    val encoded = runCatching {
        XtreamInput.encodeToPlaylistUrl(
            input = XtreamInput(
                basicUrl = resolvedBasicUrl,
                username = username,
                password = password,
                type = type,
            ),
            serverProtocol = protocol,
        )
    }.getOrNull() ?: return ""
    return encoded.takeIf {
        it == it.normalizePlaylistInputForSubmission(PlaylistInputKind.URL)
    }.orEmpty()
}

private fun List<DiscoveredSubscriptionProvider>.providerFor(
    account: ProviderAccountSummary,
) = singleOrNull { discovered ->
    discovered.descriptor.providerId == account.providerId &&
        discovered.descriptor.variants.any { variant -> variant.kind == account.providerKind }
}?.descriptor
