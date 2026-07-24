package com.m3u.smartphone.ui.business.setting.fragments

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.ScrollState
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.rememberPermissionState
import com.m3u.business.setting.BackingUpAndRestoringState
import com.m3u.business.setting.ProviderDiscoveryState
import com.m3u.business.setting.ProviderOperationState
import com.m3u.business.setting.ProviderSettingFieldError
import com.m3u.business.setting.ProviderSubscriptionForm
import com.m3u.business.setting.ProviderSubscriptionFormField
import com.m3u.business.setting.SettingProperties
import com.m3u.core.foundation.architecture.preferences.PreferencesKeys
import com.m3u.core.foundation.architecture.preferences.preferenceOf
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.DataSource
import com.m3u.data.database.model.Playlist
import com.m3u.data.repository.extension.ExtensionSettingEditToken
import com.m3u.data.repository.extension.ExtensionSettingsConfiguration
import com.m3u.data.repository.plugin.InstalledPlugin
import com.m3u.data.repository.plugin.PluginAuthorizationToken
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
import com.m3u.smartphone.ui.material.components.PlaceholderField
import com.m3u.smartphone.ui.material.components.SelectionsDefaults
import com.m3u.smartphone.ui.material.ktx.checkPermissionOrRationale
import com.m3u.smartphone.ui.material.ktx.plus
import com.m3u.smartphone.ui.material.ktx.textHorizontalLabel
import com.m3u.smartphone.ui.material.model.LocalSpacing
import kotlinx.coroutines.launch

private enum class SubscriptionsFragmentPage {
    MAIN, EXTENSION_PLUGINS, EPG_PLAYLISTS, HIDDEN_STREAMS, HIDDEN_PLAYLIST_CATEGORIES
}

@Composable
private fun SubscriptionsFragmentPage.label(): String = stringResource(
    when (this) {
        SubscriptionsFragmentPage.MAIN -> string.feat_setting_label_add_playlist
        SubscriptionsFragmentPage.EXTENSION_PLUGINS -> string.feat_setting_extension_plugins
        SubscriptionsFragmentPage.EPG_PLAYLISTS -> string.feat_setting_label_epg_playlists
        SubscriptionsFragmentPage.HIDDEN_STREAMS -> string.feat_setting_label_hidden_channels
        SubscriptionsFragmentPage.HIDDEN_PLAYLIST_CATEGORIES ->
            string.feat_setting_label_hidden_playlist_groups
    }
)

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
    providerOperationState: ProviderOperationState,
    onSelectSubscriptionProvider: (String) -> Unit,
    onSelectSubscriptionProviderKind: (String) -> Unit,
    onUpdateSubscriptionProviderSetting: (String, String?) -> Unit,
    onRetryProviderDiscovery: () -> Unit,
    onReauthenticateProviderAccount: (String) -> Unit,
    onRefreshExtensionPlugins: () -> Unit,
    onEnableExtensionPlugin: (String, String, PluginAuthorizationToken) -> Unit,
    onReauthorizeExtensionPlugin: (String, String, PluginAuthorizationToken) -> Unit,
    onDisableExtensionPlugin: (String) -> Unit,
    onRevokeExtensionPlugin: (String, String, String?) -> Unit,
    onClearExtensionData: (String, String, String?) -> Unit,
    onExportExtensionDiagnostics: (String) -> Unit,
    onOpenExtensionSettings: (String) -> Unit,
    onCloseExtensionSettings: () -> Unit,
    onUpdateExtensionSetting: (String, String, ExtensionSettingEditToken, String?) -> Unit,
    entryGeneration: Int,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues()
) {
    val spacing = LocalSpacing.current
    val coroutineScope = rememberCoroutineScope()
    val externalExtensionsEnabled by preferenceOf(PreferencesKeys.EXTERNAL_EXTENSIONS)
    val pagerState = rememberPagerState(initialPage = 0) { SubscriptionsFragmentPage.entries.size }
    val tabScrollState = remember { ScrollState(initial = 0) }

    LaunchedEffect(entryGeneration, pagerState.settledPage) {
        if (pagerState.settledPage == SubscriptionsFragmentPage.MAIN.ordinal) {
            tabScrollState.scrollTo(0)
        }
    }

    Column(modifier = modifier) {
        SecondaryScrollableTabRow(
            selectedTabIndex = pagerState.currentPage,
            scrollState = tabScrollState,
            edgePadding = spacing.medium,
            modifier = Modifier.fillMaxWidth(),
        ) {
            SubscriptionsFragmentPage.entries.forEachIndexed { index, page ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    },
                    text = { Text(page.label()) },
                    modifier = Modifier.testTag("subscriptions-page-${page.name.lowercase()}"),
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            HorizontalPager(
                state = pagerState,
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxSize(),
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
                            providerOperationState = providerOperationState,
                            onSelectSubscriptionProvider = onSelectSubscriptionProvider,
                            onSelectSubscriptionProviderKind = onSelectSubscriptionProviderKind,
                            onUpdateSubscriptionProviderSetting = onUpdateSubscriptionProviderSetting,
                            onRetryProviderDiscovery = onRetryProviderDiscovery,
                            onReauthenticateProviderAccount = onReauthenticateProviderAccount,
                            backup = backup,
                            restore = restore,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = contentPadding,
                        )
                    }

                    SubscriptionsFragmentPage.EPG_PLAYLISTS -> {
                        EpgsContentImpl(
                            epgs = epgs,
                            onDeleteEpgPlaylist = onDeleteEpgPlaylist,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = contentPadding,
                        )
                    }

                    SubscriptionsFragmentPage.HIDDEN_STREAMS -> {
                        HiddenStreamContentImpl(
                            hiddenChannels = hiddenChannels,
                            onUnhideChannel = onUnhideChannel,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = contentPadding,
                        )
                    }

                    SubscriptionsFragmentPage.HIDDEN_PLAYLIST_CATEGORIES -> {
                        HiddenPlaylistCategoriesContentImpl(
                            hiddenCategoriesWithPlaylists = hiddenCategoriesWithPlaylists,
                            onUnhidePlaylistCategory = onUnhidePlaylistCategory,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = contentPadding,
                        )
                    }

                    SubscriptionsFragmentPage.EXTENSION_PLUGINS -> {
                        ExtensionPluginsContent(
                            plugins = extensionPlugins,
                            settings = extensionSettings,
                            externalExtensionsEnabled = externalExtensionsEnabled,
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
                            contentPadding = contentPadding,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExtensionPluginsContent(
    plugins: List<InstalledPlugin>,
    settings: ExtensionSettingsConfiguration?,
    externalExtensionsEnabled: Boolean,
    onRefresh: () -> Unit,
    onEnable: (String, String, PluginAuthorizationToken) -> Unit,
    onReauthorize: (String, String, PluginAuthorizationToken) -> Unit,
    onDisable: (String) -> Unit,
    onRevoke: (String, String, String?) -> Unit,
    onClearData: (String, String, String?) -> Unit,
    onExportDiagnostics: (String) -> Unit,
    onOpenSettings: (String) -> Unit,
    onCloseSettings: () -> Unit,
    onUpdateSetting: (String, String, ExtensionSettingEditToken, String?) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    var pendingTrust by remember { mutableStateOf<InstalledPlugin?>(null) }
    var pendingReauthorization by remember { mutableStateOf(false) }
    var pendingRevoke by remember { mutableStateOf<InstalledPlugin?>(null) }
    var pendingClear by remember { mutableStateOf<InstalledPlugin?>(null) }
    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding + PaddingValues(LocalSpacing.current.medium),
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
                TextButton(
                    onClick = onRefresh,
                    enabled = externalExtensionsEnabled,
                ) {
                    Text(stringResource(string.feat_setting_codec_pack_refresh))
                }
            }
        }
        if (!externalExtensionsEnabled) {
            item { Text(stringResource(string.feat_setting_extension_enable_external_hint)) }
        } else if (plugins.isEmpty()) {
            item { Text(stringResource(string.feat_setting_extension_no_plugins)) }
        }
        if (externalExtensionsEnabled) {
            items(
                count = plugins.size,
                key = { index -> "${plugins[index].packageName}/${plugins[index].serviceName}" },
            ) { index ->
                val plugin = plugins[index]
                val extensionId = plugin.extensionId
                val actions = plugin.actionAvailability()
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        plugin.displayName ?: plugin.packageName,
                        style = MaterialTheme.typography.titleMedium,
                    )
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
                    val unapprovedNetworkOrigins =
                        plugin.networkOrigins - plugin.approvedNetworkOrigins
                    if (plugin.trusted && unapprovedNetworkOrigins.isNotEmpty()) {
                        Text(
                            text = stringResource(
                                string.feat_setting_extension_network_reauthorization_required,
                                unapprovedNetworkOrigins.sorted().joinToString(),
                            ),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    plugin.inspectionError?.let {
                        Text(
                            stringResource(string.feat_setting_extension_inspection_failed),
                            color = MaterialTheme.colorScheme.error,
                        )
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
                        if (actions.settings && extensionId != null) {
                            FilledTonalButton(onClick = { onOpenSettings(extensionId) }) {
                                Text(stringResource(string.feat_setting_extension_settings))
                            }
                        }
                        if (actions.disable && extensionId != null) {
                            FilledTonalButton(onClick = { onDisable(extensionId) }) {
                                Text(stringResource(string.feat_setting_extension_disable))
                            }
                        }
                        if (actions.enable) {
                            Button(onClick = { pendingTrust = plugin }) {
                                Text(stringResource(string.feat_setting_extension_enable))
                            }
                        }
                        if (actions.revoke) {
                            TextButton(onClick = { pendingRevoke = plugin }) {
                                Text(stringResource(string.feat_setting_extension_revoke))
                            }
                        }
                        if (actions.reauthorize) {
                            TextButton(onClick = {
                                pendingReauthorization = true
                                pendingTrust = plugin
                            }) {
                                Text(stringResource(string.feat_setting_extension_reauthorize))
                            }
                        }
                        if (actions.exportDiagnostics && extensionId != null) {
                            TextButton(onClick = { onExportDiagnostics(extensionId) }) {
                                Text(
                                    stringResource(
                                        string.feat_setting_extension_export_diagnostics
                                    )
                                )
                            }
                        }
                        if (actions.clearData) {
                            TextButton(onClick = { pendingClear = plugin }) {
                                Text(stringResource(string.feat_setting_extension_clear_data))
                            }
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
                    plugin.previousCertificateSha256?.let { previousCertificate ->
                        Text(
                            text = stringResource(
                                string.feat_setting_extension_certificate_repin,
                                previousCertificate.chunked(16).joinToString(" "),
                                plugin.certificateSha256.chunked(16).joinToString(" "),
                            ),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Text(
                        stringResource(string.feat_setting_extension_requested_capabilities),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(extensionCapabilitySummary(plugin))
                    Text(
                        stringResource(string.feat_setting_extension_network_origins),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(plugin.networkOrigins.sorted().joinToString("\n").ifEmpty { "—" })
                    if (plugin.networkOriginSettingFields.isNotEmpty()) {
                        Text(
                            stringResource(
                                string.feat_setting_extension_network_origin_settings,
                                plugin.networkOriginSettingFields.sorted().joinToString(),
                            )
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val reauthorize = pendingReauthorization
                        pendingTrust = null
                        pendingReauthorization = false
                        plugin.authorizationToken?.let { authorizationToken ->
                            if (reauthorize) {
                                onReauthorize(
                                    plugin.packageName,
                                    plugin.serviceName,
                                    authorizationToken,
                                )
                            } else {
                                onEnable(
                                    plugin.packageName,
                                    plugin.serviceName,
                                    authorizationToken,
                                )
                            }
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
                    onRevoke(plugin.packageName, plugin.serviceName, plugin.extensionId)
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
                    onClearData(plugin.packageName, plugin.serviceName, plugin.extensionId)
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
    providerOperationState: ProviderOperationState,
    onSelectSubscriptionProvider: (String) -> Unit,
    onSelectSubscriptionProviderKind: (String) -> Unit,
    onUpdateSubscriptionProviderSetting: (String, String?) -> Unit,
    onRetryProviderDiscovery: () -> Unit,
    onReauthenticateProviderAccount: (String) -> Unit,
    backup: () -> Unit,
    restore: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val spacing = LocalSpacing.current
    val clipboardManager = LocalClipboardManager.current
    val helper = LocalHelper.current
    val remoteControl by preferenceOf(PreferencesKeys.REMOTE_CONTROL)
    val providerOperationInProgress = providerOperationState.isBusy
    val providerSubmissionInProgress = providerOperationState.isSubmitting

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(spacing.small),
        contentPadding = contentPadding + PaddingValues(spacing.medium),
        modifier = modifier.imePadding()
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
                ),
                enabled = !providerOperationInProgress,
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
                            inProgress = providerOperationState.isReauthenticating(
                                account.playlistUrl
                            ),
                            enabled = !providerOperationInProgress,
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
                DataSource.Emby, DataSource.Jellyfin -> EmbyCompatibleInputContent(
                    enabled = !providerOperationInProgress,
                )
                DataSource.Provider -> DynamicProviderInputContent(
                    discoveryState = providerDiscoveryState,
                    form = providerSubscriptionForm,
                    onSelectProvider = onSelectSubscriptionProvider,
                    onSelectKind = onSelectSubscriptionProviderKind,
                    onUpdateField = onUpdateSubscriptionProviderSetting,
                    onRetry = onRetryProviderDiscovery,
                    enabled = !providerOperationInProgress,
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
                    enabled = !properties.forTvState.value && !providerOperationInProgress,
                )
            }
            if (
                remoteControl &&
                properties.selectedState.value in REMOTE_TV_SUBSCRIPTION_SOURCES
            ) {
                RemoteControlSubscribeSwitch(
                    checked = properties.forTvState.value,
                    onChanged = { properties.forTvState.value = !properties.forTvState.value },
                    enabled = !properties.localStorageState.value &&
                        !providerOperationInProgress,
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
                        modifier = Modifier
                            .weight(1f)
                            .testTag("subscription-submit-action"),
                        enabled = !providerOperationInProgress &&
                            (
                                properties.selectedState.value != DataSource.Provider ||
                                    (
                                        providerDiscoveryState is ProviderDiscoveryState.Ready &&
                                            providerSubscriptionForm != null
                                    )
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
                        if (providerSubmissionInProgress) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(18.dp)
                                    .testTag("provider-subscription-progress"),
                                color = LocalContentColor.current,
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.size(8.dp))
                        }
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
    }
}

@Composable
private fun ProviderReauthenticationCard(
    account: ProviderAccountSummary,
    inProgress: Boolean,
    enabled: Boolean,
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
            if (account.requiresExtensionOwnerConfirmation) {
                Text(
                    text = stringResource(
                        string.feat_setting_provider_owner_claim_notice
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            FilledTonalButton(
                onClick = onReauthenticate,
                enabled = enabled && !inProgress,
                modifier = Modifier.testTag("provider-reauthenticate-action"),
            ) {
                if (inProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(18.dp)
                            .testTag("provider-reauthentication-progress"),
                        color = LocalContentColor.current,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.size(8.dp))
                }
                Text(stringResource(string.feat_setting_provider_reauthenticate))
            }
        }
    }
}


@Composable
private fun EpgsContentImpl(
    epgs: List<Playlist>,
    onDeleteEpgPlaylist: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val spacing = LocalSpacing.current
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding + PaddingValues(spacing.medium),
    ) {
        item {
            Text(
                text = stringResource(string.feat_setting_label_epg_playlists),
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.textHorizontalLabel()
            )
        }
        items(epgs.size) { index ->
            val epgPlaylist = epgs[index]
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
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val spacing = LocalSpacing.current
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding + PaddingValues(spacing.medium),
    ) {
        item {
            Text(
                text = stringResource(string.feat_setting_label_hidden_channels),
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.textHorizontalLabel()
            )
        }
        items(hiddenChannels.size) { index ->
            val channel = hiddenChannels[index]
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
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val spacing = LocalSpacing.current
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding + PaddingValues(spacing.medium),
    ) {
        item {
            Text(
                text = stringResource(string.feat_setting_label_hidden_playlist_groups),
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.textHorizontalLabel()
            )
        }
        items(hiddenCategoriesWithPlaylists.size) { index ->
            val (playlist, category) = hiddenCategoriesWithPlaylists[index]
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
private fun EmbyCompatibleInputContent(
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
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
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        )
        PlaceholderField(
            text = properties.basicUrlState.value,
            placeholder = stringResource(string.feat_setting_placeholder_basic_url).uppercase(),
            onValueChange = { properties.basicUrlState.value = it },
            imeAction = ImeAction.Next,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        )
        PlaceholderField(
            text = properties.usernameState.value,
            placeholder = stringResource(string.feat_setting_placeholder_username).uppercase(),
            onValueChange = { properties.usernameState.value = it },
            imeAction = ImeAction.Next,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        )
        PlaceholderField(
            text = properties.passwordState.value,
            placeholder = stringResource(string.feat_setting_placeholder_password).uppercase(),
            onValueChange = { properties.passwordState.value = it },
            keyboardType = KeyboardType.Password,
            visualTransformation = PasswordVisualTransformation(),
            enabled = enabled,
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
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val providers = (discoveryState as? ProviderDiscoveryState.Ready)
        ?.providers
        .orEmpty()
        .map { provider -> provider.descriptor }
    LaunchedEffect(providers, form?.providerId, enabled) {
        if (enabled && providers.none { provider -> provider.providerId == form?.providerId }) {
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
            enabled = enabled,
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
                        enabled = enabled,
                        modifier = Modifier.testTag("provider-discovery-retry"),
                    ) {
                        Text(stringResource(string.feat_setting_provider_discovery_retry))
                    }
                }
                return@Column
            }

            is ProviderDiscoveryState.Ready -> Unit
        }
        FlowRow(
            modifier = Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            providers.forEach { provider ->
                val active = provider.providerId == selected?.providerId
                ProviderChoiceButton(
                    selected = active,
                    enabled = enabled,
                    onClick = {
                        onSelectProvider(provider.providerId.value)
                    },
                    text = provider.displayName,
                )
            }
        }
        selected?.let { provider ->
            FlowRow(
                modifier = Modifier.selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(spacing.small),
            ) {
                provider.variants.forEach { variant ->
                    ProviderChoiceButton(
                        selected = variant.kind == form?.providerKind,
                        enabled = enabled,
                        onClick = { onSelectKind(variant.kind.value) },
                        text = variant.displayName,
                    )
                }
            }
            form?.fields?.forEach { field ->
                ProviderFormField(
                    field = field,
                    enabled = enabled,
                    onUpdate = { value -> onUpdateField(field.definition.key, value) },
                )
            }
        }
    }
}

@Composable
private fun ProviderFormField(
    field: ProviderSubscriptionFormField,
    enabled: Boolean,
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
                enabled = enabled,
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
                modifier = Modifier.selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(spacing.small),
            ) {
                ProviderResetChoice(field, enabled, onUpdate)
                ProviderChoiceButton(
                    selected = field.value == "true" && !field.isUsingDefault,
                    enabled = enabled,
                    onClick = { onUpdate("true") },
                    text = stringResource(string.feat_setting_provider_value_true),
                )
                ProviderChoiceButton(
                    selected = field.value == "false" && !field.isUsingDefault,
                    enabled = enabled,
                    onClick = { onUpdate("false") },
                    text = stringResource(string.feat_setting_provider_value_false),
                )
            }

            ExtensionSettingType.SINGLE_CHOICE -> FlowRow(
                modifier = Modifier.selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(spacing.small),
            ) {
                ProviderResetChoice(field, enabled, onUpdate)
                definition.choices.forEach { choice ->
                    ProviderChoiceButton(
                        selected = field.value == choice.value && !field.isUsingDefault,
                        enabled = enabled,
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
    enabled: Boolean,
    onUpdate: (String?) -> Unit,
) {
    if (field.definition.defaultValue != null || !field.definition.required) {
        ProviderChoiceButton(
            selected = field.isUsingDefault || field.value == null,
            enabled = enabled,
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
    enabled: Boolean = true,
    onClick: () -> Unit,
    text: String,
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        modifier = Modifier.semantics { role = Role.RadioButton },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (selected) {
                    Icons.Rounded.CheckCircle
                } else {
                    Icons.Rounded.RadioButtonUnchecked
                },
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(text)
        }
    }
}

private fun ProviderSettingFieldError.messageResource(): Int = when (this) {
    ProviderSettingFieldError.REQUIRED -> string.feat_setting_provider_error_required
    ProviderSettingFieldError.TOO_LONG -> string.feat_setting_provider_error_too_long
    ProviderSettingFieldError.INVALID_NUMBER -> string.feat_setting_provider_error_number
    ProviderSettingFieldError.INVALID_BOOLEAN -> string.feat_setting_provider_error_boolean
    ProviderSettingFieldError.INVALID_CHOICE -> string.feat_setting_provider_error_choice
}

private val REMOTE_TV_SUBSCRIPTION_SOURCES = setOf(
    DataSource.M3U,
    DataSource.EPG,
    DataSource.Xtream,
)

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
