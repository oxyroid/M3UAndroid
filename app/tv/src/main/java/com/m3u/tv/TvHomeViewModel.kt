package com.m3u.tv

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.m3u.core.foundation.architecture.preferences.PreferencesKeys
import com.m3u.core.foundation.architecture.preferences.Settings
import com.m3u.core.foundation.architecture.preferences.set
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.DataSource
import com.m3u.data.database.model.Playlist
import com.m3u.data.repository.channel.ChannelRepository
import com.m3u.data.repository.extension.ExtensionSettingUpdateResult
import com.m3u.data.repository.extension.ExtensionSettingsConfiguration
import com.m3u.data.repository.extension.ExtensionSettingsRepository
import com.m3u.data.repository.playlist.PlaylistRepository
import com.m3u.data.repository.plugin.ExtensionPluginRepository
import com.m3u.data.repository.plugin.InstalledPlugin
import com.m3u.data.repository.plugin.PluginDataClearResult
import com.m3u.data.repository.plugin.PluginEnableResult
import com.m3u.data.repository.tv.TvRepository
import com.m3u.data.service.DPadReactionService
import com.m3u.data.service.MediaCommand
import com.m3u.data.service.PlayerManager
import com.m3u.extension.api.ExtensionId
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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
    val extensionPluginError: String? = null,
) {
    val channelCount: Int get() = counts.values.sum()
    val heroChannel: Channel? get() = recent ?: channels.firstOrNull()
}

@HiltViewModel
class TvHomeViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val channelRepository: ChannelRepository,
    private val playerManager: PlayerManager,
    private val extensionPluginRepository: ExtensionPluginRepository,
    private val extensionSettingsRepository: ExtensionSettingsRepository,
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

    init {
        observePlaylists()
        observeFavorites()
        observeRecent()
        observeExternalExtensions()
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

    fun enableExtensionPlugin(packageName: String, serviceName: String) {
        viewModelScope.launch {
            updateExtensionPluginResult(extensionPluginRepository.enable(packageName, serviceName))
            refreshExtensionPlugins()
        }
    }

    fun reauthorizeExtensionPlugin(packageName: String, serviceName: String) {
        viewModelScope.launch {
            updateExtensionPluginResult(extensionPluginRepository.reauthorize(packageName, serviceName))
            refreshExtensionPlugins()
        }
    }

    fun disableExtensionPlugin(extensionId: String) {
        if (state.value.extensionSettings?.extensionId?.value == extensionId) {
            closeExtensionSettings()
        }
        viewModelScope.launch {
            extensionPluginRepository.disable(extensionId)
            refreshExtensionPlugins()
        }
    }

    fun revokeExtensionPlugin(packageName: String, serviceName: String) {
        val revokedExtensionId = state.value.extensionPlugins
            .firstOrNull { it.packageName == packageName && it.serviceName == serviceName }
            ?.extensionId
        if (state.value.extensionSettings?.extensionId?.value == revokedExtensionId) {
            closeExtensionSettings()
        }
        viewModelScope.launch {
            extensionPluginRepository.revoke(packageName, serviceName)
            refreshExtensionPlugins()
        }
    }

    fun openExtensionSettings(extensionId: String, localeTag: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val configuration = extensionSettingsRepository.configuration(
                ExtensionId(extensionId),
                localeTag,
                TV_SETTINGS_SURFACE,
            )
            _state.update { it.copy(extensionSettings = configuration) }
        }
    }

    fun closeExtensionSettings() {
        _state.update { it.copy(extensionSettings = null) }
    }

    fun clearExtensionData(packageName: String, serviceName: String) {
        val extensionId = state.value.extensionPlugins
            .firstOrNull { plugin ->
                plugin.packageName == packageName && plugin.serviceName == serviceName
            }
            ?.extensionId
        if (state.value.extensionSettings?.extensionId?.value == extensionId) {
            closeExtensionSettings()
        }
        viewModelScope.launch(Dispatchers.IO) {
            when (val result = extensionPluginRepository.clearData(packageName, serviceName)) {
                is PluginDataClearResult.Cleared -> Unit
                is PluginDataClearResult.Rejected -> {
                    _state.update { it.copy(extensionPluginError = result.reason) }
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
        rawValue: String?,
        localeTag: String?,
    ) {
        val extensionId = state.value.extensionSettings?.extensionId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val result = extensionSettingsRepository.update(
                extensionId,
                sectionId,
                fieldKey,
                rawValue,
                localeTag,
                TV_SETTINGS_SURFACE,
            )
            if (result is ExtensionSettingUpdateResult.Updated) {
                val configuration = extensionSettingsRepository.configuration(
                    extensionId,
                    localeTag,
                    TV_SETTINGS_SURFACE,
                )
                _state.update { it.copy(extensionSettings = configuration) }
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
        }
    }

    private fun updateExtensionPluginResult(result: PluginEnableResult) {
        _state.update { state ->
            state.copy(
                extensionPluginError = when (result) {
                    is PluginEnableResult.Enabled -> null
                    is PluginEnableResult.Rejected -> result.reason
                }
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
