package com.m3u.tv

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.m3u.business.setting.ExtensionSettingsOperationQueue
import com.m3u.business.setting.ProviderDiscoveryState
import com.m3u.business.setting.ProviderSubscriptionForm
import com.m3u.business.setting.ProviderSubscriptionFormBuildResult
import com.m3u.core.foundation.architecture.preferences.PreferencesKeys
import com.m3u.core.foundation.architecture.preferences.Settings
import com.m3u.core.foundation.architecture.preferences.set
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.DataSource
import com.m3u.data.database.model.Playlist
import com.m3u.data.repository.channel.ChannelRepository
import com.m3u.data.repository.extension.ExtensionSettingEditToken
import com.m3u.data.repository.extension.ExtensionSettingUpdateResult
import com.m3u.data.repository.extension.ExtensionSettingsConfiguration
import com.m3u.data.repository.extension.ExtensionSettingsRepository
import com.m3u.data.repository.playlist.PlaylistRepository
import com.m3u.data.repository.plugin.ExtensionPluginRepository
import com.m3u.data.repository.plugin.InstalledPlugin
import com.m3u.data.repository.plugin.PluginAuthorizationToken
import com.m3u.data.repository.plugin.PluginDataClearResult
import com.m3u.data.repository.plugin.PluginEnableResult
import com.m3u.data.repository.provider.DiscoveredSubscriptionProvider
import com.m3u.data.repository.provider.ProviderAccountSummary
import com.m3u.data.repository.provider.ProviderDiscoveryException
import com.m3u.data.repository.provider.SubscriptionProviderRepository
import com.m3u.data.repository.tv.TvRepository
import com.m3u.data.service.DPadReactionService
import com.m3u.data.service.MediaCommand
import com.m3u.data.service.PlayerManager
import com.m3u.extension.api.ExtensionId
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class TvUiState(
    val playlists: List<Playlist> = emptyList(),
    val counts: Map<Playlist, Int> = emptyMap(),
    val selectedPlaylist: Playlist? = null,
    val channels: List<Channel> = emptyList(),
    val favorites: List<Channel> = emptyList(),
    val recent: Channel? = null,
    val loadingChannels: Boolean = false,
    val externalExtensionsEnabled: Boolean = false,
    val extensionPlugins: List<InstalledPlugin> = emptyList(),
    val extensionSettings: ExtensionSettingsConfiguration? = null,
    val extensionPluginOperationFailed: Boolean = false,
    val providerDiscoveryState: ProviderDiscoveryState = ProviderDiscoveryState.Loading,
    val providerAccounts: List<ProviderAccountSummary> = emptyList(),
    val providerSubscriptionForm: ProviderSubscriptionForm? = null,
    val providerSubscriptionTitle: String = "",
    val providerSubscriptionInProgress: Boolean = false,
    val providerSubscriptionFeedback: TvProviderSubscriptionFeedback? = null,
) {
    val channelCount: Int get() = counts.values.sum()
    val heroChannel: Channel? get() = recent ?: channels.firstOrNull()
}

sealed interface TvProviderSubscriptionFeedback {
    data object InvalidSettings : TvProviderSubscriptionFeedback
    data object Failed : TvProviderSubscriptionFeedback
    data class Added(val channelCount: Int) : TvProviderSubscriptionFeedback
}

@HiltViewModel
class TvHomeViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val channelRepository: ChannelRepository,
    private val playerManager: PlayerManager,
    private val extensionPluginRepository: ExtensionPluginRepository,
    private val extensionSettingsRepository: ExtensionSettingsRepository,
    private val subscriptionProviderRepository: SubscriptionProviderRepository,
    private val settings: Settings,
    tvRepository: TvRepository,
    dPadReactionService: DPadReactionService
) : ViewModel() {
    private val _state = MutableStateFlow(TvUiState())
    val state: StateFlow<TvUiState> = _state.asStateFlow()
    private val _extensionDiagnostics = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val extensionDiagnostics = _extensionDiagnostics.asSharedFlow()

    val player: StateFlow<Player?> = playerManager.player
    val currentChannel: StateFlow<Channel?> = playerManager.channel
    val isPlaying: StateFlow<Boolean> = playerManager.isPlaying
    val playbackState: StateFlow<Int> = playerManager.playbackState
    val remoteControlCode: StateFlow<Int?> = tvRepository.broadcastCodeOnTv
    val remoteDirections = dPadReactionService.incoming
    private var loadChannelsJob: Job? = null
    private var providerDiscoveryJob: Job? = null
    private var providerSubscriptionJob: Job? = null
    private var extensionSettingsLoadJob: Job? = null
    private var extensionSettingsRequestedId: ExtensionId? = null
    private var extensionSettingsGeneration = 0L
    private var extensionSettingsUpdateGeneration = 0L
    private val extensionSettingsOperationQueue = ExtensionSettingsOperationQueue(
        scope = viewModelScope,
        onFailure = {
            _state.update {
                it.copy(
                    extensionPluginOperationFailed = true,
                )
            }
        },
    )

    init {
        observePlaylists()
        observeFavorites()
        observeRecent()
        observeExternalExtensions()
        observeProviderAccounts()
        refreshSubscriptionProviders()
    }

    fun selectPlaylist(playlist: Playlist) {
        if (_state.value.selectedPlaylist?.url == playlist.url) return
        _state.update { it.copy(selectedPlaylist = playlist) }
        loadChannels(playlist.url)
    }

    fun refreshSelectedPlaylist() {
        val playlist = state.value.selectedPlaylist ?: return
        viewModelScope.launch {
            playlistRepository.refresh(playlist.url)
            loadChannels(playlist.url)
        }
    }

    fun play(channel: Channel) {
        viewModelScope.launch {
            playerManager.play(MediaCommand.Common(channel.id))
            channelRepository.reportPlayed(channel.id)
        }
    }

    fun playRecent() {
        state.value.recent?.let(::play)
    }

    fun toggleFavorite(channel: Channel) {
        viewModelScope.launch {
            channelRepository.favouriteOrUnfavourite(channel.id)
        }
    }

    fun pauseOrContinue(continuePlayback: Boolean) {
        playerManager.pauseOrContinue(continuePlayback)
    }

    fun releasePlayer() {
        playerManager.release()
    }

    fun setExternalExtensionsEnabled(enabled: Boolean) {
        viewModelScope.launch { settings[PreferencesKeys.EXTERNAL_EXTENSIONS] = enabled }
    }

    fun enableExtensionPlugin(
        packageName: String,
        serviceName: String,
        authorizationToken: PluginAuthorizationToken,
    ) {
        viewModelScope.launch {
            updateExtensionPluginResult(
                extensionPluginRepository.enable(
                    packageName,
                    serviceName,
                    authorizationToken,
                )
            )
            refreshExtensionPlugins()
        }
    }

    fun reauthorizeExtensionPlugin(
        packageName: String,
        serviceName: String,
        authorizationToken: PluginAuthorizationToken,
    ) {
        viewModelScope.launch {
            updateExtensionPluginResult(
                extensionPluginRepository.reauthorize(
                    packageName,
                    serviceName,
                    authorizationToken,
                )
            )
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
            reportExtensionOperationFailure()
            return
        }
        closeExtensionSettingsIfActive(extensionId)
        extensionSettingsOperationQueue.launchDestructive(extensionId) {
            extensionPluginRepository.revoke(packageName, serviceName)
            _state.update {
                it.copy(
                    extensionPluginOperationFailed = false,
                )
            }
            refreshExtensionPlugins()
        }
    }

    fun openExtensionSettings(extensionId: String, localeTag: String?) {
        val requestedExtensionId = ExtensionId(extensionId)
        val generation = ++extensionSettingsGeneration
        extensionSettingsLoadJob?.cancel()
        extensionSettingsRequestedId = requestedExtensionId
        _state.update { it.copy(extensionSettings = null) }
        extensionSettingsLoadJob = extensionSettingsOperationQueue.launchOperation(extensionId) {
            val configuration = withContext(Dispatchers.IO) {
                extensionSettingsRepository.configuration(
                    requestedExtensionId,
                    localeTag,
                    TV_SETTINGS_SURFACE,
                )
            }
            if (generation == extensionSettingsGeneration) {
                _state.update {
                    it.copy(
                        extensionSettings = configuration,
                        extensionPluginOperationFailed = false,
                    )
                }
            }
        }
    }

    fun closeExtensionSettings() {
        extensionSettingsGeneration++
        extensionSettingsLoadJob?.cancel()
        extensionSettingsLoadJob = null
        extensionSettingsRequestedId = null
        _state.update { it.copy(extensionSettings = null) }
    }

    private fun closeExtensionSettingsIfActive(extensionId: String) {
        if (
            state.value.extensionSettings?.extensionId?.value == extensionId ||
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
            reportExtensionOperationFailure()
            return
        }
        closeExtensionSettingsIfActive(extensionId)
        extensionSettingsOperationQueue.launchDestructive(extensionId) {
            when (
                val result = withContext(Dispatchers.IO) {
                    extensionPluginRepository.clearData(packageName, serviceName)
                }
            ) {
                is PluginDataClearResult.Cleared -> {
                    _state.update {
                        it.copy(
                            extensionPluginOperationFailed = false,
                        )
                    }
                }
                is PluginDataClearResult.Rejected -> {
                    _state.update {
                        it.copy(
                            extensionPluginOperationFailed = true,
                        )
                    }
                }
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
        val extensionId = state.value.extensionSettings?.extensionId ?: return
        val generation = extensionSettingsGeneration
        val updateGeneration = ++extensionSettingsUpdateGeneration
        extensionSettingsOperationQueue.launchUpdate(extensionId.value) update@{
            val result = withContext(Dispatchers.IO) {
                val update = extensionSettingsRepository.update(
                    extensionId,
                    sectionId,
                    fieldKey,
                    editToken,
                    rawValue,
                )
                TvExtensionSettingsRefreshResult(
                    configuration = extensionSettingsRepository.configuration(
                        extensionId,
                        localeTag,
                        TV_SETTINGS_SURFACE,
                    ),
                    rejected = update is ExtensionSettingUpdateResult.Rejected,
                )
            }
            if (
                generation != extensionSettingsGeneration ||
                updateGeneration != extensionSettingsUpdateGeneration ||
                state.value.extensionSettings?.extensionId != extensionId
            ) {
                return@update
            }
            _state.update {
                it.copy(
                    extensionSettings = result.configuration,
                    extensionPluginOperationFailed = result.rejected,
                )
            }
        }
    }

    fun refreshSubscriptionProviders() {
        providerDiscoveryJob?.cancel()
        providerDiscoveryJob = viewModelScope.launch {
            _state.update { state ->
                state.copy(providerDiscoveryState = ProviderDiscoveryState.Loading)
            }
            try {
                val providers = withContext(Dispatchers.IO) {
                    subscriptionProviderRepository.discoverProviders()
                }
                _state.update { state ->
                    val formAvailable = state.providerSubscriptionForm?.let { form ->
                        providers.any { provider ->
                            provider.descriptor.providerId == form.providerId &&
                                provider.descriptor.variants.any { variant ->
                                    variant.kind == form.providerKind
                                }
                        }
                    } != false
                    state.copy(
                        providerDiscoveryState = if (providers.isEmpty()) {
                            ProviderDiscoveryState.Empty
                        } else {
                            ProviderDiscoveryState.Ready(providers)
                        },
                        providerSubscriptionForm = state.providerSubscriptionForm
                            .takeIf { formAvailable },
                        providerSubscriptionTitle = state.providerSubscriptionTitle
                            .takeIf { formAvailable }
                            .orEmpty(),
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.update { state ->
                    state.copy(
                        providerDiscoveryState = ProviderDiscoveryState.Failed(
                            failureCount = (error as? ProviderDiscoveryException)?.failureCount,
                        )
                    )
                }
            }
        }
    }

    fun openProviderSubscription(providerId: String, providerKind: String) {
        val descriptor = currentProviders().firstOrNull { provider ->
            provider.descriptor.providerId.value == providerId
        }?.descriptor ?: return
        val kind = descriptor.variants.firstOrNull { variant ->
            variant.kind.value == providerKind
        }?.kind ?: return
        _state.update { state ->
            state.copy(
                providerSubscriptionForm = ProviderSubscriptionForm.create(descriptor, kind),
                providerSubscriptionTitle = descriptor.displayName,
                providerSubscriptionFeedback = null,
            )
        }
    }

    fun reauthenticateProviderAccount(playlistUrl: String) {
        val account = state.value.providerAccounts.firstOrNull { summary ->
            summary.playlistUrl == playlistUrl && summary.requiresReauthentication
        } ?: return
        providerDiscoveryJob?.cancel()
        providerDiscoveryJob = viewModelScope.launch {
            val providers = currentProviders().ifEmpty {
                loadProvidersForAction() ?: return@launch
            }
            val descriptor = providers.singleOrNull { provider ->
                provider.descriptor.providerId == account.providerId &&
                    provider.descriptor.variants.any { variant ->
                        variant.kind == account.providerKind
                    }
            }?.descriptor ?: run {
                _state.update {
                    it.copy(providerSubscriptionFeedback = TvProviderSubscriptionFeedback.Failed)
                }
                return@launch
            }
            _state.update { current ->
                current.copy(
                    providerSubscriptionForm = ProviderSubscriptionForm.createForReauthentication(
                        descriptor = descriptor,
                        account = account,
                    ),
                    providerSubscriptionTitle = account.playlistTitle,
                    providerSubscriptionFeedback = null,
                )
            }
        }
    }

    fun closeProviderSubscription() {
        if (state.value.providerSubscriptionInProgress) return
        _state.update { current ->
            current.copy(
                providerSubscriptionForm = null,
                providerSubscriptionTitle = "",
                providerSubscriptionFeedback = null,
            )
        }
    }

    fun updateProviderSubscriptionTitle(title: String) {
        _state.update {
            it.copy(providerSubscriptionTitle = title, providerSubscriptionFeedback = null)
        }
    }

    fun selectProviderKind(kindValue: String) {
        val form = state.value.providerSubscriptionForm ?: return
        val descriptor = currentProviders().firstOrNull { provider ->
            provider.descriptor.providerId == form.providerId
        }?.descriptor ?: return
        val kind = descriptor.variants.firstOrNull { variant ->
            variant.kind.value == kindValue
        }?.kind ?: return
        if (kind == form.providerKind) return
        _state.update { current ->
            current.copy(
                providerSubscriptionForm = ProviderSubscriptionForm.create(descriptor, kind),
                providerSubscriptionFeedback = null,
            )
        }
    }

    fun updateProviderSetting(fieldKey: String, value: String?) {
        _state.update { current ->
            current.copy(
                providerSubscriptionForm = current.providerSubscriptionForm?.update(fieldKey, value),
                providerSubscriptionFeedback = null,
            )
        }
    }

    fun submitProviderSubscription() {
        if (providerSubscriptionJob?.isActive == true) return
        val current = state.value
        val form = current.providerSubscriptionForm ?: return
        if (current.providerSubscriptionTitle.isBlank()) {
            _state.update {
                it.copy(providerSubscriptionFeedback = TvProviderSubscriptionFeedback.InvalidSettings)
            }
            return
        }
        val buildResult = runCatching {
            form.buildRequest(
                title = current.providerSubscriptionTitle,
                stageCredential = subscriptionProviderRepository::stageCredential,
            )
        }.getOrElse {
            _state.update {
                it.copy(providerSubscriptionFeedback = TvProviderSubscriptionFeedback.Failed)
            }
            return
        }
        when (val result = buildResult) {
            is ProviderSubscriptionFormBuildResult.Invalid -> {
                _state.update {
                    it.copy(
                        providerSubscriptionForm = result.form,
                        providerSubscriptionFeedback = TvProviderSubscriptionFeedback.InvalidSettings,
                    )
                }
            }

            is ProviderSubscriptionFormBuildResult.Ready -> {
                providerSubscriptionJob = viewModelScope.launch {
                    _state.update {
                        it.copy(
                            providerSubscriptionInProgress = true,
                            providerSubscriptionFeedback = null,
                        )
                    }
                    try {
                        val subscription = withContext(Dispatchers.IO) {
                            subscriptionProviderRepository.subscribe(result.request)
                        }
                        _state.update {
                            it.copy(
                                providerSubscriptionForm = null,
                                providerSubscriptionTitle = "",
                                providerSubscriptionInProgress = false,
                                providerSubscriptionFeedback = TvProviderSubscriptionFeedback.Added(
                                    subscription.channelCount
                                ),
                            )
                        }
                    } catch (cancelled: CancellationException) {
                        _state.update { it.copy(providerSubscriptionInProgress = false) }
                        throw cancelled
                    } catch (_: Exception) {
                        _state.update {
                            it.copy(
                                providerSubscriptionInProgress = false,
                                providerSubscriptionFeedback = TvProviderSubscriptionFeedback.Failed,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun observeExternalExtensions() {
        viewModelScope.launch {
            settings.data
                .map { preferences -> preferences[PreferencesKeys.EXTERNAL_EXTENSIONS] ?: false }
                .collect { enabled ->
                    _state.update { it.copy(externalExtensionsEnabled = enabled) }
                    refreshExtensionPlugins()
                }
        }
    }

    private fun refreshExtensionPlugins() {
        viewModelScope.launch(Dispatchers.IO) {
            val plugins = extensionPluginRepository.installedPlugins()
            _state.update { it.copy(extensionPlugins = plugins) }
            refreshSubscriptionProviders()
        }
    }

    private fun observeProviderAccounts() {
        viewModelScope.launch {
            subscriptionProviderRepository.observeAccountSummaries().collect { accounts ->
                _state.update { it.copy(providerAccounts = accounts) }
            }
        }
    }

    private fun currentProviders(): List<DiscoveredSubscriptionProvider> =
        (state.value.providerDiscoveryState as? ProviderDiscoveryState.Ready)
            ?.providers
            .orEmpty()

    private suspend fun loadProvidersForAction(): List<DiscoveredSubscriptionProvider>? {
        _state.update { it.copy(providerDiscoveryState = ProviderDiscoveryState.Loading) }
        return try {
            val providers = withContext(Dispatchers.IO) {
                subscriptionProviderRepository.discoverProviders()
            }
            _state.update {
                it.copy(
                    providerDiscoveryState = if (providers.isEmpty()) {
                        ProviderDiscoveryState.Empty
                    } else {
                        ProviderDiscoveryState.Ready(providers)
                    }
                )
            }
            providers
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            _state.update {
                it.copy(
                    providerDiscoveryState = ProviderDiscoveryState.Failed(
                        failureCount = (error as? ProviderDiscoveryException)?.failureCount,
                    )
                )
            }
            null
        }
    }

    private fun updateExtensionPluginResult(result: PluginEnableResult) {
        _state.update { state ->
            state.copy(
                extensionPluginOperationFailed = result is PluginEnableResult.Rejected,
            )
        }
    }

    private fun reportExtensionOperationFailure() {
        _state.update {
            it.copy(
                extensionPluginOperationFailed = true,
            )
        }
    }

    private fun observePlaylists() {
        viewModelScope.launch {
            playlistRepository
                .observeAllCounts()
                .flowOn(Dispatchers.Default)
                .collect { counts ->
                    val state = _state.value
                    val playlists = counts.keys
                        .filterNot { it.source == DataSource.EPG }
                        .sortedBy { it.title.lowercase() }
                    val previous = state.selectedPlaylist
                    val selected = previous
                        ?.let { active -> playlists.firstOrNull { it.url == active.url } }
                        ?: playlists.firstOrNull()
                    val previousCount = previous?.let { playlist -> state.counts.countFor(playlist.url) }
                    val selectedCount = selected?.let { playlist -> counts.countFor(playlist.url) }

                    _state.update {
                        it.copy(
                            playlists = playlists,
                            counts = counts,
                            selectedPlaylist = selected
                        )
                    }

                    if (selected != null && (selected.url != previous?.url || selectedCount != previousCount)) {
                        loadChannels(selected.url)
                    }
                }
        }
    }

    private fun observeFavorites() {
        viewModelScope.launch {
            channelRepository.observeAllFavorite().collect { favorites ->
                _state.update { it.copy(favorites = favorites) }
            }
        }
    }

    private fun observeRecent() {
        viewModelScope.launch {
            channelRepository.observePlayedRecently().collect { recent ->
                _state.update { it.copy(recent = recent) }
            }
        }
    }

    private fun loadChannels(url: String) {
        loadChannelsJob?.cancel()
        loadChannelsJob = viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(loadingChannels = true) }
            val channels = channelRepository
                .getByPlaylistUrl(url)
                .filterNot { it.hidden }
                .sortedWith(
                    compareBy<Channel> { it.category.lowercase() }
                        .thenBy { it.title.lowercase() }
                )
            _state.update { state ->
                if (state.selectedPlaylist?.url == url) {
                    state.copy(
                        channels = channels,
                        loadingChannels = false
                    )
                } else {
                    state
                }
            }
        }
    }

    private fun Map<Playlist, Int>.countFor(url: String): Int? =
        entries.firstOrNull { it.key.url == url }?.value

    private companion object {
        const val TV_SETTINGS_SURFACE = "tv"
    }
}

private data class TvExtensionSettingsRefreshResult(
    val configuration: ExtensionSettingsConfiguration?,
    val rejected: Boolean,
)
