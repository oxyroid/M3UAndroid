package com.m3u.smartphone.ui.business.setting.fragments

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.rememberPermissionState
import com.m3u.business.setting.BackingUpAndRestoringState
import com.m3u.business.setting.ProviderDiscoveryState
import com.m3u.business.setting.ProviderSettingFieldError
import com.m3u.business.setting.ProviderSubscriptionForm
import com.m3u.business.setting.ProviderSubscriptionFormField
import com.m3u.business.setting.SettingProperties
import com.m3u.core.foundation.architecture.preferences.PreferencesKeys
import com.m3u.core.foundation.architecture.preferences.preferenceOf
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.DataSource
import com.m3u.data.database.model.Playlist
import com.m3u.data.repository.extension.ExtensionSettingsConfiguration
import com.m3u.data.repository.plugin.InstalledPlugin
import com.m3u.data.repository.provider.ProviderAccountSummary
import com.m3u.extension.api.ExtensionSettingType
import com.m3u.extension.api.ExtensionState
import com.m3u.i18n.R.string
import com.m3u.smartphone.benchmark.DebugBenchmarkSettings
import com.m3u.smartphone.ui.business.setting.components.DataSourceSelection
import com.m3u.smartphone.ui.business.setting.components.EpgPlaylistItem
import com.m3u.smartphone.ui.business.setting.components.HiddenChannelItem
import com.m3u.smartphone.ui.business.setting.components.HiddenPlaylistGroupItem
import com.m3u.smartphone.ui.business.setting.components.LocalStorageButton
import com.m3u.smartphone.ui.business.setting.components.LocalStorageSwitch
import com.m3u.smartphone.ui.business.setting.components.RemoteControlSubscribeSwitch
import com.m3u.smartphone.ui.common.helper.LocalHelper
import com.m3u.smartphone.ui.material.components.HorizontalPagerIndicator
import com.m3u.smartphone.ui.material.components.PlaceholderField
import com.m3u.smartphone.ui.material.components.SelectionsDefaults
import com.m3u.smartphone.ui.material.ktx.checkPermissionOrRationale
import com.m3u.smartphone.ui.material.ktx.textHorizontalLabel
import com.m3u.smartphone.ui.material.model.LocalSpacing

private enum class SubscriptionsFragmentPage {
    MAIN, EPG_PLAYLISTS, HIDDEN_STREAMS, HIDDEN_PLAYLIST_CATEGORIES, EXTENSION_PLUGINS
}

@Composable
context(_: SettingProperties)
internal fun SubscriptionsFragment(
    backingUpOrRestoring: BackingUpAndRestoringState,
    hiddenChannels: List<Channel>,
    hiddenCategoriesWithPlaylists: List<Pair<Playlist, String>>,
    onUnhideChannel: (Int) -> Unit,
    onUnhidePlaylistCategory: (playlistUrl: String, category: String) -> Unit,
    onClipboard: (String) -> Unit,
    onSubscribe: () -> Unit,
    backup: () -> Unit,
    restore: () -> Unit,
    epgs: List<Playlist>,
    onDeleteEpgPlaylist: (String) -> Unit,
    extensionPlugins: List<InstalledPlugin>,
    extensionSettings: ExtensionSettingsConfiguration?,
    providerDiscoveryState: ProviderDiscoveryState,
    providerAccountSummaries: List<ProviderAccountSummary>,
    providerSubscriptionForm: ProviderSubscriptionForm?,
    onSelectSubscriptionProvider: (String) -> Unit,
    onSelectSubscriptionProviderKind: (String) -> Unit,
    onUpdateSubscriptionProviderSetting: (String, String?) -> Unit,
    onRetryProviderDiscovery: () -> Unit,
    onReauthenticateProviderAccount: (String) -> Unit,
    onRefreshExtensionPlugins: () -> Unit,
    onEnableExtensionPlugin: (String, String) -> Unit,
    onReauthorizeExtensionPlugin: (String, String) -> Unit,
    onDisableExtensionPlugin: (String) -> Unit,
    onRevokeExtensionPlugin: (String, String) -> Unit,
    onClearExtensionData: (String, String) -> Unit,
    onExportExtensionDiagnostics: (String) -> Unit,
    onOpenExtensionSettings: (String) -> Unit,
    onCloseExtensionSettings: () -> Unit,
    onUpdateExtensionSetting: (String, String, String?) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues()
) {
    val spacing = LocalSpacing.current
    val pagerState = rememberPagerState(initialPage = 0) { SubscriptionsFragmentPage.entries.size }

    Box {
        HorizontalPager(
            state = pagerState,
            verticalAlignment = Alignment.Top,
            contentPadding = contentPadding,
            modifier = modifier,
            key = { SubscriptionsFragmentPage.entries[it] },
            pageSize = PageSize.Fill,
            pageSpacing = 1.dp
        ) { page ->
            when (SubscriptionsFragmentPage.entries[page]) {
                SubscriptionsFragmentPage.MAIN -> {
                    MainContentImpl(
                        backingUpOrRestoring = backingUpOrRestoring,
                        onClipboard = onClipboard,
                        onSubscribe = onSubscribe,
                        providerDiscoveryState = providerDiscoveryState,
                        providerAccountSummaries = providerAccountSummaries,
                        providerSubscriptionForm = providerSubscriptionForm,
                        onSelectSubscriptionProvider = onSelectSubscriptionProvider,
                        onSelectSubscriptionProviderKind = onSelectSubscriptionProviderKind,
                        onUpdateSubscriptionProviderSetting = onUpdateSubscriptionProviderSetting,
                        onRetryProviderDiscovery = onRetryProviderDiscovery,
                        onReauthenticateProviderAccount = onReauthenticateProviderAccount,
                        backup = backup,
                        restore = restore,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                SubscriptionsFragmentPage.EPG_PLAYLISTS -> {
                    EpgsContentImpl(
                        epgs = epgs,
                        onDeleteEpgPlaylist = onDeleteEpgPlaylist,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                SubscriptionsFragmentPage.HIDDEN_STREAMS -> {
                    HiddenStreamContentImpl(
                        hiddenChannels = hiddenChannels,
                        onUnhideChannel = onUnhideChannel,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                SubscriptionsFragmentPage.HIDDEN_PLAYLIST_CATEGORIES -> {
                    HiddenPlaylistCategoriesContentImpl(
                        hiddenCategoriesWithPlaylists = hiddenCategoriesWithPlaylists,
                        onUnhidePlaylistCategory = onUnhidePlaylistCategory,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                SubscriptionsFragmentPage.EXTENSION_PLUGINS -> {
                    ExtensionPluginsContent(
                        plugins = extensionPlugins,
                        settings = extensionSettings,
                        onRefresh = onRefreshExtensionPlugins,
                        onEnable = onEnableExtensionPlugin,
                        onReauthorize = onReauthorizeExtensionPlugin,
                        onDisable = onDisableExtensionPlugin,
                        onRevoke = onRevokeExtensionPlugin,
                        onClearData = onClearExtensionData,
                        onExportDiagnostics = onExportExtensionDiagnostics,
                        onOpenSettings = onOpenExtensionSettings,
                        onCloseSettings = onCloseExtensionSettings,
                        onUpdateSetting = onUpdateExtensionSetting,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        HorizontalPagerIndicator(
            pagerState = pagerState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(contentPadding)
                .padding(spacing.medium)
        )
    }
}

@Composable
private fun ExtensionPluginsContent(
    plugins: List<InstalledPlugin>,
    settings: ExtensionSettingsConfiguration?,
    onRefresh: () -> Unit,
    onEnable: (String, String) -> Unit,
    onReauthorize: (String, String) -> Unit,
    onDisable: (String) -> Unit,
    onRevoke: (String, String) -> Unit,
    onClearData: (String, String) -> Unit,
    onExportDiagnostics: (String) -> Unit,
    onOpenSettings: (String) -> Unit,
    onCloseSettings: () -> Unit,
    onUpdateSetting: (String, String, String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingTrust by remember { mutableStateOf<InstalledPlugin?>(null) }
    var pendingReauthorization by remember { mutableStateOf(false) }
    var pendingRevoke by remember { mutableStateOf<InstalledPlugin?>(null) }
    var pendingClear by remember { mutableStateOf<InstalledPlugin?>(null) }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(LocalSpacing.current.medium),
        verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.medium),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(string.feat_setting_extension_plugins),
                    style = MaterialTheme.typography.titleLarge,
                )
                TextButton(onClick = onRefresh) {
                    Text(stringResource(string.feat_setting_codec_pack_refresh))
                }
            }
        }
        if (plugins.isEmpty()) {
            item { Text(stringResource(string.feat_setting_extension_no_plugins)) }
        }
        items(plugins.size, key = { index -> "${plugins[index].packageName}/${plugins[index].serviceName}" }) { index ->
            val plugin = plugins[index]
            val extensionId = plugin.extensionId
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(plugin.displayName ?: plugin.packageName, style = MaterialTheme.typography.titleMedium)
                plugin.developer?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                plugin.version?.let { Text("v$it", style = MaterialTheme.typography.bodySmall) }
                Text(extensionStateLabel(plugin.state), style = MaterialTheme.typography.labelMedium)
                Text(plugin.serviceName, style = MaterialTheme.typography.bodySmall)
                Text(plugin.certificateSha256, style = MaterialTheme.typography.labelSmall)
                if (plugin.grantedCapabilities.isNotEmpty()) {
                    Text(
                        plugin.grantedCapabilities.sorted().joinToString(),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                plugin.inspectionError?.let { error ->
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
                if (!plugin.installed) {
                    Text(
                        stringResource(string.feat_setting_extension_not_installed),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (plugin.signatureChanged) {
                    Text(
                        stringResource(string.feat_setting_extension_signature_changed),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (plugin.enabled && plugin.state == ExtensionState.ENABLED && extensionId != null) {
                        FilledTonalButton(onClick = { onOpenSettings(extensionId) }) {
                            Text(stringResource(string.feat_setting_extension_settings))
                        }
                        FilledTonalButton(onClick = { onDisable(extensionId) }) {
                            Text(stringResource(string.feat_setting_extension_disable))
                        }
                    } else if (
                        plugin.installed &&
                        !plugin.signatureChanged &&
                        plugin.inspectionError == null
                    ) {
                        Button(onClick = { pendingTrust = plugin }) {
                            Text(stringResource(string.feat_setting_extension_enable))
                        }
                    }
                    if (plugin.trusted || plugin.signatureChanged) {
                        TextButton(onClick = { pendingRevoke = plugin }) {
                            Text(stringResource(string.feat_setting_extension_revoke))
                        }
                    }
                    if (plugin.installed && plugin.trusted && !plugin.signatureChanged) {
                        TextButton(onClick = {
                            pendingReauthorization = true
                            pendingTrust = plugin
                        }) {
                            Text(stringResource(string.feat_setting_extension_reauthorize))
                        }
                    }
                    if (plugin.installed && extensionId != null) {
                        TextButton(onClick = { onExportDiagnostics(extensionId) }) {
                            Text(stringResource(string.feat_setting_extension_export_diagnostics))
                        }
                    }
                    if (plugin.canClearData) {
                        TextButton(onClick = { pendingClear = plugin }) {
                            Text(stringResource(string.feat_setting_extension_clear_data))
                        }
                    }
                }
            }
        }
    }
    pendingTrust?.let { plugin ->
        AlertDialog(
            onDismissRequest = {
                pendingTrust = null
                pendingReauthorization = false
            },
            title = { Text(stringResource(string.feat_setting_extension_confirm_title)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        stringResource(
                            string.feat_setting_extension_confirm_identity,
                            plugin.packageName,
                            plugin.certificateSha256.chunked(16).joinToString(" "),
                            plugin.displayName.orEmpty(),
                            plugin.developer.orEmpty(),
                            plugin.version.orEmpty(),
                        )
                    )
                    Text(
                        stringResource(string.feat_setting_extension_requested_capabilities),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(extensionCapabilitySummary(plugin))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val reauthorize = pendingReauthorization
                        pendingTrust = null
                        pendingReauthorization = false
                        if (reauthorize) {
                            onReauthorize(plugin.packageName, plugin.serviceName)
                        } else {
                            onEnable(plugin.packageName, plugin.serviceName)
                        }
                    }
                ) {
                    Text(
                        stringResource(
                            if (pendingReauthorization) {
                                string.feat_setting_extension_reauthorize
                            } else {
                                string.feat_setting_extension_enable
                            }
                        )
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingTrust = null
                    pendingReauthorization = false
                }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
    pendingRevoke?.let { plugin ->
        AlertDialog(
            onDismissRequest = { pendingRevoke = null },
            title = { Text(stringResource(string.feat_setting_extension_forget_title)) },
            text = { Text(stringResource(string.feat_setting_extension_forget_body)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingRevoke = null
                    onRevoke(plugin.packageName, plugin.serviceName)
                }) {
                    Text(stringResource(string.feat_setting_extension_revoke))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRevoke = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
    settings?.let { configuration ->
        ExtensionSettingsDialog(
            configuration = configuration,
            onDismiss = onCloseSettings,
            onUpdate = onUpdateSetting,
        )
    }
    pendingClear?.let { plugin ->
        AlertDialog(
            onDismissRequest = { pendingClear = null },
            title = { Text(stringResource(string.feat_setting_extension_clear_data_title)) },
            text = { Text(stringResource(string.feat_setting_extension_clear_data_body)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingClear = null
                    onClearData(plugin.packageName, plugin.serviceName)
                }) { Text(stringResource(string.feat_setting_extension_clear_data)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingClear = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun extensionCapabilitySummary(plugin: InstalledPlugin): String {
    val required = stringResource(string.feat_setting_extension_capability_required)
    val optional = stringResource(string.feat_setting_extension_capability_optional)
    return plugin.capabilityPermissions.joinToString("\n") { permission ->
        val requirement = if (permission.required) required else optional
        "${permission.id} ($requirement) — ${permission.reason}"
    }.ifEmpty { "—" }
}

@Composable
private fun extensionStateLabel(state: ExtensionState): String = stringResource(
    when (state) {
        ExtensionState.ENABLED -> string.feat_setting_extension_state_enabled
        ExtensionState.DISABLED -> string.feat_setting_extension_state_disabled
        ExtensionState.INCOMPATIBLE -> string.feat_setting_extension_state_incompatible
        ExtensionState.UNHEALTHY -> string.feat_setting_extension_state_unhealthy
    }
)

@Composable
context(properties: SettingProperties)
private fun MainContentImpl(
    backingUpOrRestoring: BackingUpAndRestoringState,
    onClipboard: (String) -> Unit,
    onSubscribe: () -> Unit,
    providerDiscoveryState: ProviderDiscoveryState,
    providerAccountSummaries: List<ProviderAccountSummary>,
    providerSubscriptionForm: ProviderSubscriptionForm?,
    onSelectSubscriptionProvider: (String) -> Unit,
    onSelectSubscriptionProviderKind: (String) -> Unit,
    onUpdateSubscriptionProviderSetting: (String, String?) -> Unit,
    onRetryProviderDiscovery: () -> Unit,
    onReauthenticateProviderAccount: (String) -> Unit,
    backup: () -> Unit,
    restore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val clipboardManager = LocalClipboardManager.current
    val helper = LocalHelper.current
    val remoteControl by preferenceOf(PreferencesKeys.REMOTE_CONTROL)

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(spacing.small),
        contentPadding = PaddingValues(spacing.medium),
        modifier = modifier
    ) {
        item {
            DataSourceSelection(
                selectedState = properties.selectedState,
                supported = listOf(
                    DataSource.M3U,
                    DataSource.EPG,
                    DataSource.Xtream,
                    DataSource.Emby,
                    DataSource.Jellyfin,
                    DataSource.Provider,
                )
            )
        }

        val reauthenticationAccounts = providerAccountSummaries.filter { account ->
            account.requiresReauthentication
        }
        if (reauthenticationAccounts.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
                    reauthenticationAccounts.forEach { account ->
                        ProviderReauthenticationCard(
                            account = account,
                            onReauthenticate = {
                                onReauthenticateProviderAccount(account.playlistUrl)
                            },
                        )
                    }
                }
            }
        }

        item {
            when (properties.selectedState.value) {
                DataSource.M3U -> M3UInputContent()
                DataSource.EPG -> EPGInputContent()
                DataSource.Xtream -> XtreamInputContent()
                DataSource.Emby, DataSource.Jellyfin -> EmbyCompatibleInputContent()
                DataSource.Provider -> DynamicProviderInputContent(
                    discoveryState = providerDiscoveryState,
                    form = providerSubscriptionForm,
                    onSelectProvider = onSelectSubscriptionProvider,
                    onSelectKind = onSelectSubscriptionProviderKind,
                    onUpdateField = onUpdateSubscriptionProviderSetting,
                    onRetry = onRetryProviderDiscovery,
                )
                DataSource.Dropbox -> {}
            }
        }

        item {
            Spacer(Modifier.size(spacing.medium))
        }
        item {
            if (properties.selectedState.value == DataSource.M3U) {
                LocalStorageSwitch(
                    checked = properties.localStorageState.value,
                    onChanged = { properties.localStorageState.value = it },
                    enabled = !properties.forTvState.value
                )
            }
            if (remoteControl) {
                RemoteControlSubscribeSwitch(
                    checked = properties.forTvState.value,
                    onChanged = { properties.forTvState.value = !properties.forTvState.value },
                    enabled = !properties.localStorageState.value
                )
            }
        }
        item {
            @SuppressLint("InlinedApi")
            val postNotificationPermission = rememberPermissionState(
                Manifest.permission.POST_NOTIFICATIONS
            )
            Column {
                Row {
                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = properties.selectedState.value != DataSource.Provider ||
                            (
                                providerDiscoveryState is ProviderDiscoveryState.Ready &&
                                    providerSubscriptionForm != null
                            ),
                        onClick = {
                            postNotificationPermission.checkPermissionOrRationale(
                                showRationale = {
                                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .apply {
                                            putExtra(
                                                Settings.EXTRA_APP_PACKAGE,
                                                helper.activityContext.packageName
                                            )
                                        }
                                    helper.activityContext.startActivity(intent)
                                },
                                block = {
                                    onSubscribe()
                                }
                            )
                        }
                    ) {
                        Text(stringResource(string.feat_setting_label_subscribe).uppercase())
                    }
                    when (properties.selectedState.value) {
                        DataSource.M3U, DataSource.Xtream -> {
                            IconButton(
                                onClick = {
                                    onClipboard(clipboardManager.getText()?.text.orEmpty())
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ContentPaste,
                                    contentDescription = null
                                )
                            }
                        }

                        else -> {}
                    }
                }
                val backupText = stringResource(string.feat_setting_label_backup).uppercase()
                val restoreText = stringResource(string.feat_setting_label_restore).uppercase()


                TextButton(
                    onClick = backup,
                    enabled = backingUpOrRestoring == BackingUpAndRestoringState.NONE,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        text = backupText
                    )
                }
                TextButton(
                    onClick = restore,
                    enabled = backingUpOrRestoring == BackingUpAndRestoringState.NONE,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        text = restoreText
                    )
                }
            }

        }

        item {
            Spacer(Modifier.imePadding())
        }
    }
}

@Composable
private fun ProviderReauthenticationCard(
    account: ProviderAccountSummary,
    onReauthenticate: () -> Unit,
) {
    val spacing = LocalSpacing.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("provider-reauthentication"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(spacing.medium),
            verticalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Warning, contentDescription = null)
                Text(
                    text = stringResource(
                        string.feat_setting_provider_reauthentication_required,
                        account.playlistTitle,
                    ),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Text(
                text = stringResource(
                    string.feat_setting_provider_account_summary,
                    account.serverName,
                    account.username,
                    account.baseUrl,
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            FilledTonalButton(
                onClick = onReauthenticate,
                modifier = Modifier.testTag("provider-reauthenticate-action"),
            ) {
                Text(stringResource(string.feat_setting_provider_reauthenticate))
            }
        }
    }
}


@Composable
private fun EpgsContentImpl(
    epgs: List<Playlist>,
    onDeleteEpgPlaylist: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = stringResource(string.feat_setting_label_epg_playlists),
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.textHorizontalLabel()
        )
        epgs.forEach { epgPlaylist ->
            EpgPlaylistItem(
                epgPlaylist = epgPlaylist,
                onDeleteEpgPlaylist = { onDeleteEpgPlaylist(epgPlaylist.url) }
            )
        }
    }
}

@Composable
private fun HiddenStreamContentImpl(
    hiddenChannels: List<Channel>,
    onUnhideChannel: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = stringResource(string.feat_setting_label_hidden_channels),
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.textHorizontalLabel()
        )
        hiddenChannels.forEach { channel ->
            HiddenChannelItem(
                channel = channel,
                onHidden = { onUnhideChannel(channel.id) }
            )
        }
    }
}

@Composable
private fun HiddenPlaylistCategoriesContentImpl(
    hiddenCategoriesWithPlaylists: List<Pair<Playlist, String>>,
    onUnhidePlaylistCategory: (playlistUrl: String, category: String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth()) {
        Text(
            text = stringResource(string.feat_setting_label_hidden_playlist_groups),
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.textHorizontalLabel()
        )
        hiddenCategoriesWithPlaylists.forEach { (playlist, category) ->
            HiddenPlaylistGroupItem(
                playlist = playlist,
                group = category,
                onHidden = { onUnhidePlaylistCategory(playlist.url, category) }
            )
        }
    }
}

@Composable
context(properties: SettingProperties)
private fun M3UInputContent(
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        properties.applyBenchmarkPlaylistPrefill(DebugBenchmarkSettings.from(context))
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.small)
    ) {
        PlaceholderField(
            text = properties.titleState.value,
            placeholder = stringResource(string.feat_setting_placeholder_title).uppercase(),
            onValueChange = { properties.titleState.value = Uri.decode(it) },
            imeAction = ImeAction.Next,
            modifier = Modifier.fillMaxWidth()
        )
        Crossfade(
            targetState = properties.localStorageState.value,
            label = "url"
        ) { localStorage ->
            if (!localStorage) {
                PlaceholderField(
                    text = properties.urlState.value,
                    placeholder = stringResource(string.feat_setting_placeholder_url).uppercase(),
                    onValueChange = { properties.urlState.value = Uri.decode(it) },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                LocalStorageButton(
                    titleState = properties.titleState,
                    uriState = properties.uriState,
                )
            }
        }
    }
}

private fun SettingProperties.applyBenchmarkPlaylistPrefill(settings: DebugBenchmarkSettings) {
    settings.getString(DebugBenchmarkSettings.PLAYLIST_TITLE)
        ?.let { titleState.value = it }
    settings.getString(DebugBenchmarkSettings.PLAYLIST_URL)
        ?.let { urlState.value = it }
}

@Composable
context(properties: SettingProperties)
private fun EPGInputContent(
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.small)
    ) {
        PlaceholderField(
            text = properties.titleState.value,
            placeholder = stringResource(string.feat_setting_placeholder_epg_title).uppercase(),
            onValueChange = { properties.titleState.value = Uri.decode(it) },
            modifier = Modifier.fillMaxWidth()
        )
        PlaceholderField(
            text = properties.epgState.value,
            placeholder = stringResource(string.feat_setting_placeholder_epg).uppercase(),
            onValueChange = { properties.epgState.value = Uri.decode(it) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
context(properties: SettingProperties)
private fun XtreamInputContent(modifier: Modifier = Modifier) {
    val spacing = LocalSpacing.current

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.small)
    ) {
        PlaceholderField(
            text = properties.titleState.value,
            placeholder = stringResource(string.feat_setting_placeholder_title).uppercase(),
            onValueChange = { properties.titleState.value = Uri.decode(it) },
            modifier = Modifier.fillMaxWidth()
        )
        PlaceholderField(
            text = properties.basicUrlState.value,
            placeholder = stringResource(string.feat_setting_placeholder_basic_url).uppercase(),
            onValueChange = { properties.basicUrlState.value = it },
            modifier = Modifier.fillMaxWidth()
        )
        PlaceholderField(
            text = properties.usernameState.value,
            placeholder = stringResource(string.feat_setting_placeholder_username).uppercase(),
            onValueChange = { properties.usernameState.value = it },
            modifier = Modifier.fillMaxWidth()
        )
        PlaceholderField(
            text = properties.passwordState.value,
            placeholder = stringResource(string.feat_setting_placeholder_password).uppercase(),
            onValueChange = { properties.passwordState.value = it },
            keyboardType = KeyboardType.Password,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Warning(stringResource(string.feat_setting_warning_xtream_takes_much_more_time))
    }
}

@Composable
context(properties: SettingProperties)
private fun EmbyCompatibleInputContent(modifier: Modifier = Modifier) {
    val spacing = LocalSpacing.current

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.small)
    ) {
        PlaceholderField(
            text = properties.titleState.value,
            placeholder = stringResource(string.feat_setting_placeholder_title).uppercase(),
            onValueChange = { properties.titleState.value = Uri.decode(it) },
            imeAction = ImeAction.Next,
            modifier = Modifier.fillMaxWidth()
        )
        PlaceholderField(
            text = properties.basicUrlState.value,
            placeholder = stringResource(string.feat_setting_placeholder_basic_url).uppercase(),
            onValueChange = { properties.basicUrlState.value = it },
            imeAction = ImeAction.Next,
            modifier = Modifier.fillMaxWidth()
        )
        PlaceholderField(
            text = properties.usernameState.value,
            placeholder = stringResource(string.feat_setting_placeholder_username).uppercase(),
            onValueChange = { properties.usernameState.value = it },
            imeAction = ImeAction.Next,
            modifier = Modifier.fillMaxWidth()
        )
        PlaceholderField(
            text = properties.passwordState.value,
            placeholder = stringResource(string.feat_setting_placeholder_password).uppercase(),
            onValueChange = { properties.passwordState.value = it },
            keyboardType = KeyboardType.Password,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
context(properties: SettingProperties)
private fun DynamicProviderInputContent(
    discoveryState: ProviderDiscoveryState,
    form: ProviderSubscriptionForm?,
    onSelectProvider: (String) -> Unit,
    onSelectKind: (String) -> Unit,
    onUpdateField: (String, String?) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val providers = (discoveryState as? ProviderDiscoveryState.Ready)
        ?.providers
        .orEmpty()
        .map { provider -> provider.descriptor }
    LaunchedEffect(providers, form?.providerId) {
        if (providers.none { provider -> provider.providerId == form?.providerId }) {
            providers.firstOrNull()?.let { provider ->
                onSelectProvider(provider.providerId.value)
            }
        }
    }
    val selected = providers.firstOrNull {
        it.providerId == form?.providerId
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        PlaceholderField(
            text = properties.titleState.value,
            placeholder = stringResource(string.feat_setting_placeholder_title).uppercase(),
            onValueChange = { properties.titleState.value = Uri.decode(it) },
            modifier = Modifier.fillMaxWidth(),
        )
        when (discoveryState) {
            ProviderDiscoveryState.Loading -> {
                Row(
                    modifier = Modifier.testTag("provider-discovery-loading"),
                    horizontalArrangement = Arrangement.spacedBy(spacing.small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text(stringResource(string.feat_setting_provider_discovery_loading))
                }
                return@Column
            }

            ProviderDiscoveryState.Empty -> {
                Text(
                    text = stringResource(string.feat_setting_provider_discovery_empty),
                    modifier = Modifier.testTag("provider-discovery-empty"),
                )
                return@Column
            }

            is ProviderDiscoveryState.Failed -> {
                Column(
                    modifier = Modifier.testTag("provider-discovery-failed"),
                    verticalArrangement = Arrangement.spacedBy(spacing.small),
                ) {
                    Text(stringResource(string.feat_setting_provider_discovery_failed))
                    FilledTonalButton(
                        onClick = onRetry,
                        modifier = Modifier.testTag("provider-discovery-retry"),
                    ) {
                        Text(stringResource(string.feat_setting_provider_discovery_retry))
                    }
                }
                return@Column
            }

            is ProviderDiscoveryState.Ready -> Unit
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
            providers.forEach { provider ->
                val active = provider.providerId == selected?.providerId
                ProviderChoiceButton(
                    selected = active,
                    onClick = {
                        onSelectProvider(provider.providerId.value)
                    },
                    text = provider.displayName,
                )
            }
        }
        selected?.let { provider ->
            FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                provider.variants.forEach { variant ->
                    ProviderChoiceButton(
                        selected = variant.kind == form?.providerKind,
                        onClick = { onSelectKind(variant.kind.value) },
                        text = variant.displayName,
                    )
                }
            }
            form?.fields?.forEach { field ->
                ProviderFormField(
                    field = field,
                    onUpdate = { value -> onUpdateField(field.definition.key, value) },
                )
            }
        }
    }
}

@Composable
private fun ProviderFormField(
    field: ProviderSubscriptionFormField,
    onUpdate: (String?) -> Unit,
) {
    val definition = field.definition
    val spacing = LocalSpacing.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.extraSmall)) {
        Text(
            text = definition.label + if (definition.required) " *" else "",
            style = MaterialTheme.typography.labelLarge,
        )
        definition.description?.let { description ->
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
        when (definition.type) {
            ExtensionSettingType.TEXT,
            ExtensionSettingType.NUMBER,
            ExtensionSettingType.SECRET -> PlaceholderField(
                text = field.value.orEmpty(),
                placeholder = definition.label,
                onValueChange = onUpdate,
                contentColor = if (field.error == null) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.error
                },
                keyboardType = when (definition.type) {
                    ExtensionSettingType.NUMBER -> KeyboardType.Decimal
                    ExtensionSettingType.SECRET -> KeyboardType.Password
                    else -> KeyboardType.Text
                },
                visualTransformation = if (definition.type == ExtensionSettingType.SECRET) {
                    PasswordVisualTransformation()
                } else {
                    VisualTransformation.None
                },
                modifier = Modifier.fillMaxWidth(),
            )

            ExtensionSettingType.BOOLEAN -> FlowRow(
                horizontalArrangement = Arrangement.spacedBy(spacing.small),
            ) {
                ProviderResetChoice(field, onUpdate)
                ProviderChoiceButton(
                    selected = field.value == "true" && !field.isUsingDefault,
                    onClick = { onUpdate("true") },
                    text = stringResource(string.feat_setting_provider_value_true),
                )
                ProviderChoiceButton(
                    selected = field.value == "false" && !field.isUsingDefault,
                    onClick = { onUpdate("false") },
                    text = stringResource(string.feat_setting_provider_value_false),
                )
            }

            ExtensionSettingType.SINGLE_CHOICE -> FlowRow(
                horizontalArrangement = Arrangement.spacedBy(spacing.small),
            ) {
                ProviderResetChoice(field, onUpdate)
                definition.choices.forEach { choice ->
                    ProviderChoiceButton(
                        selected = field.value == choice.value && !field.isUsingDefault,
                        onClick = { onUpdate(choice.value) },
                        text = choice.label,
                    )
                }
            }
        }
        if (field.isUsingDefault) {
            Text(
                text = stringResource(
                    string.feat_setting_provider_default_value,
                    field.value.orEmpty(),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        field.error?.let { error ->
            Text(
                text = stringResource(error.messageResource()),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ProviderResetChoice(
    field: ProviderSubscriptionFormField,
    onUpdate: (String?) -> Unit,
) {
    if (field.definition.defaultValue != null || !field.definition.required) {
        ProviderChoiceButton(
            selected = field.isUsingDefault || field.value == null,
            onClick = { onUpdate(null) },
            text = stringResource(
                if (field.definition.defaultValue == null) {
                    string.feat_setting_provider_value_not_set
                } else {
                    string.feat_setting_provider_value_default
                }
            ),
        )
    }
}

@Composable
private fun ProviderChoiceButton(
    selected: Boolean,
    onClick: () -> Unit,
    text: String,
) {
    FilledTonalButton(
        onClick = onClick,
        colors = if (selected) {
            ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            ButtonDefaults.filledTonalButtonColors()
        },
    ) {
        Text(text)
    }
}

private fun ProviderSettingFieldError.messageResource(): Int = when (this) {
    ProviderSettingFieldError.REQUIRED -> string.feat_setting_provider_error_required
    ProviderSettingFieldError.TOO_LONG -> string.feat_setting_provider_error_too_long
    ProviderSettingFieldError.INVALID_NUMBER -> string.feat_setting_provider_error_number
    ProviderSettingFieldError.INVALID_BOOLEAN -> string.feat_setting_provider_error_boolean
    ProviderSettingFieldError.INVALID_CHOICE -> string.feat_setting_provider_error_choice
}

@Composable
private fun Warning(
    text: String,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    CompositionLocalProvider(
        LocalContentColor provides LocalContentColor.current.copy(0.54f)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.small),
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
        ) {
            Icon(imageVector = Icons.Rounded.Warning, contentDescription = null)
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}
