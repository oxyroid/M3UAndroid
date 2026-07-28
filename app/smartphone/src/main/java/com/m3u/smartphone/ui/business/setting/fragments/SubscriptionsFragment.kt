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
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Warning
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
import com.m3u.business.setting.ProviderSubscriptionSource
import com.m3u.business.setting.ProviderSubscriptionForm
import com.m3u.business.setting.ProviderSubscriptionFormField
import com.m3u.business.setting.SettingProperties
import com.m3u.business.setting.subscriptionSources
import com.m3u.business.setting.supports
import com.m3u.core.foundation.architecture.preferences.PreferencesKeys
import com.m3u.core.foundation.architecture.preferences.preferenceOf
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.DataSource
import com.m3u.data.database.model.Playlist
import com.m3u.data.repository.provider.ProviderAccountSummary
import com.m3u.data.repository.provider.SubscriptionProviderExecutionKind
import com.m3u.extension.api.ExtensionSettingType
import com.m3u.i18n.R.string
import com.m3u.smartphone.benchmark.DebugBenchmarkSettings
import com.m3u.smartphone.ui.business.setting.components.DataSourceSelection
import com.m3u.smartphone.ui.business.setting.components.DataSourceSelectionOption
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
import java.util.Locale
import kotlinx.coroutines.launch

private enum class SubscriptionsFragmentPage {
    MAIN, EPG_PLAYLISTS, HIDDEN_STREAMS, HIDDEN_PLAYLIST_CATEGORIES
}

@Composable
private fun SubscriptionsFragmentPage.label(): String = stringResource(
    when (this) {
        SubscriptionsFragmentPage.MAIN -> string.feat_setting_label_add_playlist
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
    providerDiscoveryState: ProviderDiscoveryState,
    providerAccountSummaries: List<ProviderAccountSummary>,
    providerSubscriptionForm: ProviderSubscriptionForm?,
    providerOperationState: ProviderOperationState,
    onSelectSubscriptionProviderVariant: (String, String) -> Unit,
    onUpdateSubscriptionProviderSetting: (String, String?) -> Unit,
    onRetryProviderDiscovery: () -> Unit,
    onReauthenticateProviderAccount: (String) -> Unit,
    entryGeneration: Int,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues()
) {
    val spacing = LocalSpacing.current
    val coroutineScope = rememberCoroutineScope()
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
                    modifier = Modifier.testTag(
                        "subscriptions-page-${page.name.lowercase(Locale.ROOT)}"
                    ),
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
                            onSelectSubscriptionProviderVariant =
                                onSelectSubscriptionProviderVariant,
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

                }
            }
        }
    }
}

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
    onSelectSubscriptionProviderVariant: (String, String) -> Unit,
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
    val loadingStateDescription = stringResource(string.ui_state_loading)
    val bidiFormatter = rememberUiBidiFormatter()
    val ordinarySources = listOf(
        DataSource.M3U,
        DataSource.EPG,
        DataSource.Xtream,
    )
    val providerSources = providerDiscoveryState.subscriptionSources()
    val ordinarySourceOptions = ordinarySources.map { source ->
        DataSourceSelectionOption(
            key = source.selectionKey(),
            label = stringResource(source.resId),
        )
    }
    val providerSourceOptions = providerSources.map { source ->
        val stableProviderId = bidiFormatter.ltr(source.providerId.value)
        val variantName = bidiFormatter.natural(source.displayName)
            .ifBlank { stableProviderId }
        val providerName = bidiFormatter.natural(source.providerDisplayName)
            .ifBlank { stableProviderId }
        val isExternal = source.executionKind == SubscriptionProviderExecutionKind.EXTERNAL
        DataSourceSelectionOption(
            key = source.selectionKey(),
            label = if (isExternal && providerName != variantName) {
                stringResource(
                    string.feat_setting_provider_source_with_provider,
                    variantName,
                    providerName,
                )
            } else {
                variantName
            },
            supportingLabel = stableProviderId.takeIf { isExternal },
        )
    }
    val selectedSourceKey = if (properties.selectedState.value == DataSource.Provider) {
        providerSubscriptionForm?.let { form ->
            providerSourceSelectionKey(
                providerId = form.providerId.value,
                providerKind = form.providerKind.value,
            )
        }.orEmpty()
    } else {
        properties.selectedState.value.selectionKey()
    }
    val selectedProviderFallback = providerSubscriptionForm
        ?.takeIf {
            properties.selectedState.value == DataSource.Provider &&
                providerSourceOptions.none { option -> option.key == selectedSourceKey }
        }
        ?.let { form ->
            DataSourceSelectionOption(
                key = selectedSourceKey,
                label = bidiFormatter.ltr(form.providerKind.value),
                supportingLabel = bidiFormatter.ltr(form.providerId.value),
                enabled = false,
            )
        }
    val sourceOptions = ordinarySourceOptions +
        providerSourceOptions +
        listOfNotNull(selectedProviderFallback)
    val selectedSourceLabel = sourceOptions
        .firstOrNull { option -> option.key == selectedSourceKey }
        ?.label
        ?: stringResource(properties.selectedState.value.resId)
    val ordinarySourceDiscoveryNotice = providerDiscoveryNotice(
        state = providerDiscoveryState,
        providerSelected = properties.selectedState.value == DataSource.Provider,
    )

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(spacing.small),
        contentPadding = contentPadding + PaddingValues(spacing.medium),
        modifier = modifier
            .testTag("subscription-main-content")
            .imePadding()
    ) {
        item {
            DataSourceSelection(
                selectedKey = selectedSourceKey,
                selectedLabel = selectedSourceLabel,
                options = sourceOptions,
                onSelect = { selectionKey ->
                    val ordinarySource = ordinarySources.firstOrNull { source ->
                        source.selectionKey() == selectionKey
                    }
                    if (ordinarySource != null) {
                        properties.selectedState.value = ordinarySource
                    } else {
                        providerSources.firstOrNull { source ->
                            source.selectionKey() == selectionKey
                        }?.let { source ->
                            properties.selectedState.value = DataSource.Provider
                            onSelectSubscriptionProviderVariant(
                                source.providerId.value,
                                source.providerKind.value,
                            )
                        }
                    }
                },
                enabled = !providerOperationInProgress,
            )
        }

        when (ordinarySourceDiscoveryNotice) {
            ProviderDiscoveryNotice.NONE -> Unit

            ProviderDiscoveryNotice.LOADING -> {
                item {
                    ProviderDiscoveryLoadingNotice()
                }
            }

            ProviderDiscoveryNotice.EMPTY -> {
                item {
                    ProviderDiscoveryRetryNotice(
                        message = stringResource(string.feat_setting_provider_discovery_empty),
                        onRetry = onRetryProviderDiscovery,
                        enabled = !providerOperationInProgress,
                        testTag = "provider-discovery-empty",
                    )
                }
            }

            ProviderDiscoveryNotice.FAILED -> {
                item {
                    ProviderDiscoveryRetryNotice(
                        message = stringResource(string.feat_setting_provider_discovery_failed),
                        onRetry = onRetryProviderDiscovery,
                        enabled = !providerOperationInProgress,
                        testTag = "provider-discovery-failed",
                    )
                }
            }
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
                DataSource.Provider -> DynamicProviderInputContent(
                    discoveryState = providerDiscoveryState,
                    form = providerSubscriptionForm,
                    onUpdateField = onUpdateSubscriptionProviderSetting,
                    onRetry = onRetryProviderDiscovery,
                    enabled = !providerOperationInProgress,
                )
                else -> Unit
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
                            .testTag("subscription-submit-action")
                            .semantics {
                                if (providerSubmissionInProgress) {
                                    liveRegion = LiveRegionMode.Polite
                                    stateDescription = loadingStateDescription
                                }
                            },
                        enabled = !providerOperationInProgress &&
                            (
                                properties.selectedState.value != DataSource.Provider ||
                                    providerDiscoveryState.supports(providerSubscriptionForm)
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
                        Text(
                            stringResource(
                                if (providerSubmissionInProgress) {
                                    string.feat_setting_label_subscribing
                                } else {
                                    string.feat_setting_label_subscribe
                                }
                            )
                        )
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
                                    contentDescription = stringResource(
                                        string.feat_setting_label_parse_from_clipboard
                                    )
                                )
                            }
                        }

                        else -> {}
                    }
                }
                val backupText = stringResource(string.feat_setting_label_backup)
                val restoreText = stringResource(string.feat_setting_label_restore)

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.small),
                    verticalArrangement = Arrangement.spacedBy(spacing.small),
                ) {
                    TextButton(
                        onClick = backup,
                        enabled = backingUpOrRestoring == BackingUpAndRestoringState.NONE,
                    ) {
                        Text(text = backupText)
                    }
                    TextButton(
                        onClick = restore,
                        enabled = backingUpOrRestoring == BackingUpAndRestoringState.NONE,
                    ) {
                        Text(text = restoreText)
                    }
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
    val bidiFormatter = rememberUiBidiFormatter()
    val loadingStateDescription = stringResource(string.ui_state_loading)
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
                        bidiFormatter.natural(account.playlistTitle),
                    ),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Text(
                text = stringResource(
                    string.feat_setting_provider_account_summary,
                    bidiFormatter.natural(account.serverName),
                    bidiFormatter.natural(account.username),
                    bidiFormatter.ltr(account.baseUrl),
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
                modifier = Modifier
                    .testTag("provider-reauthenticate-action")
                    .semantics {
                        if (inProgress) {
                            liveRegion = LiveRegionMode.Polite
                            stateDescription = loadingStateDescription
                        }
                    },
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
            placeholder = stringResource(string.feat_setting_placeholder_title),
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
                    placeholder = stringResource(string.feat_setting_placeholder_url),
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
            placeholder = stringResource(string.feat_setting_placeholder_epg_title),
            onValueChange = { properties.titleState.value = Uri.decode(it) },
            modifier = Modifier.fillMaxWidth()
        )
        PlaceholderField(
            text = properties.epgState.value,
            placeholder = stringResource(string.feat_setting_placeholder_epg),
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
            placeholder = stringResource(string.feat_setting_placeholder_title),
            onValueChange = { properties.titleState.value = Uri.decode(it) },
            modifier = Modifier.fillMaxWidth()
        )
        PlaceholderField(
            text = properties.basicUrlState.value,
            placeholder = stringResource(string.feat_setting_placeholder_basic_url),
            onValueChange = { properties.basicUrlState.value = it },
            modifier = Modifier.fillMaxWidth()
        )
        PlaceholderField(
            text = properties.usernameState.value,
            placeholder = stringResource(string.feat_setting_placeholder_username),
            onValueChange = { properties.usernameState.value = it },
            modifier = Modifier.fillMaxWidth()
        )
        PlaceholderField(
            text = properties.passwordState.value,
            placeholder = stringResource(string.feat_setting_placeholder_password),
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
private fun DynamicProviderInputContent(
    discoveryState: ProviderDiscoveryState,
    form: ProviderSubscriptionForm?,
    onUpdateField: (String, String?) -> Unit,
    onRetry: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val bidiFormatter = rememberUiBidiFormatter()
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        PlaceholderField(
            text = properties.titleState.value,
            placeholder = stringResource(string.feat_setting_placeholder_title),
            onValueChange = { properties.titleState.value = Uri.decode(it) },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
        when {
            discoveryState is ProviderDiscoveryState.Loading -> {
                ProviderDiscoveryLoadingNotice()
            }

            form != null && !discoveryState.supports(form) -> {
                ProviderDiscoveryRetryNotice(
                    message = stringResource(
                        string.feat_setting_provider_selected_unavailable
                    ),
                    onRetry = onRetry,
                    enabled = enabled,
                    testTag = "provider-selected-unavailable",
                )
            }

            discoveryState is ProviderDiscoveryState.Empty -> {
                ProviderDiscoveryRetryNotice(
                    message = stringResource(string.feat_setting_provider_discovery_empty),
                    onRetry = onRetry,
                    enabled = enabled,
                    testTag = "provider-discovery-empty",
                )
            }

            discoveryState is ProviderDiscoveryState.Failed -> {
                ProviderDiscoveryRetryNotice(
                    message = stringResource(string.feat_setting_provider_discovery_failed),
                    onRetry = onRetry,
                    enabled = enabled,
                    testTag = "provider-discovery-failed",
                )
            }
        }
        form?.fields?.forEach { field ->
            ProviderFormField(
                field = field,
                bidiFormatter = bidiFormatter,
                enabled = enabled,
                onUpdate = { value -> onUpdateField(field.definition.key, value) },
            )
        }
    }
}

@Composable
private fun ProviderDiscoveryLoadingNotice() {
    val spacing = LocalSpacing.current
    Row(
        modifier = Modifier.testTag("provider-discovery-loading"),
        horizontalArrangement = Arrangement.spacedBy(spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp))
        Text(
            text = stringResource(string.feat_setting_provider_discovery_loading),
            modifier = Modifier.semantics {
                liveRegion = LiveRegionMode.Polite
            },
        )
    }
}

@Composable
private fun ProviderDiscoveryRetryNotice(
    message: String,
    onRetry: () -> Unit,
    enabled: Boolean,
    testTag: String,
) {
    val spacing = LocalSpacing.current
    Column(
        modifier = Modifier.testTag(testTag),
        verticalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        Text(
            text = message,
            modifier = Modifier.semantics {
                liveRegion = LiveRegionMode.Polite
            },
        )
        FilledTonalButton(
            onClick = onRetry,
            enabled = enabled,
            modifier = Modifier.testTag("provider-discovery-retry"),
        ) {
            Text(stringResource(string.feat_setting_provider_discovery_retry))
        }
    }
}

@Composable
private fun ProviderFormField(
    field: ProviderSubscriptionFormField,
    bidiFormatter: UiBidiFormatter,
    enabled: Boolean,
    onUpdate: (String?) -> Unit,
) {
    val definition = field.definition
    val spacing = LocalSpacing.current
    val errorMessage = field.error?.let { stringResource(it.messageResource()) }
    val requiredDescription =
        stringResource(string.feat_setting_provider_error_required)
    Column(verticalArrangement = Arrangement.spacedBy(spacing.extraSmall)) {
        val displayLabel = bidiFormatter.natural(definition.label)
        Text(
            text = bidiFormatter.natural(
                if (definition.required) "${definition.label} *" else definition.label
            ),
            style = MaterialTheme.typography.labelLarge,
        )
        definition.description?.let { description ->
            Text(
                bidiFormatter.natural(description),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        when (definition.type) {
            ExtensionSettingType.TEXT,
            ExtensionSettingType.NUMBER,
            ExtensionSettingType.SECRET -> PlaceholderField(
                text = field.value.orEmpty(),
                placeholder = displayLabel,
                onValueChange = onUpdate,
                enabled = enabled,
                contentColor = if (field.error == null) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.error
                },
                placeholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
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
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        if (definition.required) {
                            stateDescription = requiredDescription
                        }
                        if (errorMessage != null) {
                            error(errorMessage)
                        }
                    },
            )

            ExtensionSettingType.BOOLEAN -> FlowRow(
                modifier = Modifier.selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(spacing.small),
                verticalArrangement = Arrangement.spacedBy(spacing.small),
            ) {
                ProviderResetChoice(
                    field = field,
                    enabled = enabled,
                    fieldLabel = definition.label,
                    requiredDescription = requiredDescription.takeIf { definition.required },
                    errorMessage = errorMessage,
                    onUpdate = onUpdate,
                )
                ProviderChoiceButton(
                    selected = field.value == "true" && !field.isUsingDefault,
                    enabled = enabled,
                    onClick = { onUpdate("true") },
                    text = stringResource(string.feat_setting_provider_value_true),
                    fieldLabel = definition.label,
                    requiredDescription = requiredDescription.takeIf { definition.required },
                    errorMessage = errorMessage,
                )
                ProviderChoiceButton(
                    selected = field.value == "false" && !field.isUsingDefault,
                    enabled = enabled,
                    onClick = { onUpdate("false") },
                    text = stringResource(string.feat_setting_provider_value_false),
                    fieldLabel = definition.label,
                    requiredDescription = requiredDescription.takeIf { definition.required },
                    errorMessage = errorMessage,
                )
            }

            ExtensionSettingType.SINGLE_CHOICE -> FlowRow(
                modifier = Modifier.selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(spacing.small),
                verticalArrangement = Arrangement.spacedBy(spacing.small),
            ) {
                ProviderResetChoice(
                    field = field,
                    enabled = enabled,
                    fieldLabel = definition.label,
                    requiredDescription = requiredDescription.takeIf { definition.required },
                    errorMessage = errorMessage,
                    onUpdate = onUpdate,
                )
                definition.choices.forEach { choice ->
                    ProviderChoiceButton(
                        selected = field.value == choice.value && !field.isUsingDefault,
                        enabled = enabled,
                        onClick = { onUpdate(choice.value) },
                        text = bidiFormatter.natural(choice.label),
                        fieldLabel = definition.label,
                        requiredDescription = requiredDescription.takeIf { definition.required },
                        errorMessage = errorMessage,
                    )
                }
            }
        }
        if (field.isUsingDefault) {
            Text(
                text = stringResource(
                    string.feat_setting_provider_default_value,
                    bidiFormatter.natural(field.value.orEmpty()),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        errorMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}

@Composable
private fun ProviderResetChoice(
    field: ProviderSubscriptionFormField,
    enabled: Boolean,
    fieldLabel: String,
    requiredDescription: String?,
    errorMessage: String?,
    onUpdate: (String?) -> Unit,
) {
    if (field.definition.defaultValue != null || !field.definition.required) {
        ProviderChoiceButton(
            selected = field.isUsingDefault || field.value == null,
            enabled = enabled,
            onClick = { onUpdate(null) },
            fieldLabel = fieldLabel,
            requiredDescription = requiredDescription,
            text = stringResource(
                if (field.definition.defaultValue == null) {
                    string.feat_setting_provider_value_not_set
                } else {
                    string.feat_setting_provider_value_default
                }
            ),
            errorMessage = errorMessage,
        )
    }
}

@Composable
private fun ProviderChoiceButton(
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
    text: String,
    fieldLabel: String,
    requiredDescription: String?,
    errorMessage: String? = null,
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
        modifier = Modifier.semantics {
            role = Role.RadioButton
            contentDescription = buildList {
                add(text.withoutBidiControls())
                add(fieldLabel.withoutBidiControls())
                requiredDescription?.let(::add)
            }.distinct().joinToString(separator = ". ")
            errorMessage?.let { message ->
                error(message)
            }
        },
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

private fun DataSource.selectionKey(): String = "data-source:$value"

private fun ProviderSubscriptionSource.selectionKey(): String =
    providerSourceSelectionKey(providerId.value, providerKind.value)

private fun providerSourceSelectionKey(
    providerId: String,
    providerKind: String,
): String = "provider:$providerId:$providerKind"

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
