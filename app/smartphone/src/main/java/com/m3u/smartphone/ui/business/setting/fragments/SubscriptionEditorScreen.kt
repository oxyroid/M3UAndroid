package com.m3u.smartphone.ui.business.setting.fragments

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
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
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.PermissionStatus
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
import com.m3u.data.database.model.DataSource
import com.m3u.data.repository.provider.ProviderAccountSummary
import com.m3u.data.repository.provider.SubscriptionProviderExecutionKind
import com.m3u.data.worker.abandonPersistedUriPermissionLease
import com.m3u.data.worker.beginPersistedUriPermissionLease
import com.m3u.extension.api.ExtensionSettingType
import com.m3u.i18n.R.string
import com.m3u.smartphone.benchmark.DebugBenchmarkSettings
import com.m3u.smartphone.ui.business.setting.components.LocalStorageButton
import com.m3u.smartphone.ui.business.setting.components.LocalStorageSwitch
import com.m3u.smartphone.ui.business.setting.components.RemoteControlSubscribeSwitch
import com.m3u.smartphone.ui.common.helper.LocalHelper
import com.m3u.smartphone.ui.material.ktx.plus
import com.m3u.smartphone.ui.material.ktx.rememberUiBidiFormatter
import com.m3u.smartphone.ui.material.ktx.UiBidiFormatter
import com.m3u.smartphone.ui.material.model.LocalSpacing
import kotlinx.coroutines.launch

@Composable
context(properties: SettingProperties)
internal fun SubscriptionEditorScreen(
    dataOperationInProgress: Boolean,
    subscriptionSubmissionBlocked: Boolean,
    sourceKey: String,
    draftKey: String,
    providerId: String?,
    providerKind: String?,
    reauthenticationPlaylistUrl: String?,
    onClipboard: (String) -> Unit,
    onBeginSubscriptionDraft: (String, DataSource) -> Unit,
    onSubscribe: () -> Unit,
    providerDiscoveryState: ProviderDiscoveryState,
    providerSubscriptionForm: ProviderSubscriptionForm?,
    providerOperationState: ProviderOperationState,
    onSelectSubscriptionProviderVariant: (String, String) -> Unit,
    onUpdateSubscriptionProviderSetting: (String, String?) -> Unit,
    onRetryProviderDiscovery: () -> Unit,
    onRetryProviderReauthentication: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val spacing = LocalSpacing.current
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val helper = LocalHelper.current
    val remoteControl by preferenceOf(PreferencesKeys.REMOTE_CONTROL)
    val operationInProgress =
        providerOperationState.isBusy ||
            dataOperationInProgress ||
            subscriptionSubmissionBlocked
    val loadingStateDescription = stringResource(string.ui_state_loading)
    val fileAccessFailure = stringResource(string.feat_setting_playlist_file_access_failed)
    var submissionAttempted by rememberSaveable(draftKey) {
        mutableStateOf(false)
    }
    var draftReady by remember(draftKey) {
        mutableStateOf(reauthenticationPlaylistUrl != null)
    }
    val bidiFormatter = rememberUiBidiFormatter()
    val ordinarySource = ordinarySubscriptionSourceOrNull(sourceKey)
    val providerSources = providerDiscoveryState.subscriptionSources()
    val providerSource = providerSources.firstOrNull { source ->
        source.subscriptionSelectionKey() == sourceKey
    }
    val expectedProviderId = providerId ?: providerSource?.providerId?.value
    val expectedProviderKind = providerKind ?: providerSource?.providerKind?.value
    val discoveredProvider =
        (providerDiscoveryState as? ProviderDiscoveryState.Ready)
            ?.providers
            .orEmpty()
            .firstOrNull { provider ->
                provider.descriptor.providerId.value == expectedProviderId
            }
    val discoveredVariant = discoveredProvider
        ?.descriptor
        ?.variants
        ?.firstOrNull { variant -> variant.kind.value == expectedProviderKind }
    val matchingProviderForm = providerSubscriptionForm?.takeIf { form ->
        expectedProviderId != null &&
            expectedProviderKind != null &&
            form.providerId.value == expectedProviderId &&
            form.providerKind.value == expectedProviderKind &&
            form.reauthenticationPlaylistUrl == reauthenticationPlaylistUrl
    }
    val isReauthentication = reauthenticationPlaylistUrl != null
    val editorSource = when {
        ordinarySource != null -> ordinarySource
        sourceKey.startsWith(PROVIDER_SOURCE_PREFIX) -> DataSource.Provider
        else -> properties.selectedState.value
    }
    val providerSubmissionInProgress = providerOperationState.submission?.let { submission ->
        editorSource == DataSource.Provider &&
            submission.providerId.value == expectedProviderId &&
            submission.providerKind.value == expectedProviderKind &&
            submission.reauthenticationPlaylistUrl == reauthenticationPlaylistUrl
    } == true
    val sourceLabel = if (editorSource == DataSource.Provider) {
        discoveredVariant?.displayName
            ?.let(bidiFormatter::natural)
            ?.takeIf(String::isNotBlank)
            ?: stringResource(DataSource.Provider.resId)
    } else {
        stringResource(editorSource.resId)
    }
    val sourceSupportingName = when (editorSource) {
        DataSource.M3U -> stringResource(
            string.feat_setting_playlist_source_m3u_description
        )
        DataSource.EPG -> stringResource(
            string.feat_setting_playlist_source_epg_description
        )
        DataSource.Xtream -> stringResource(
            string.feat_setting_playlist_source_xtream_description
        )
        DataSource.Provider -> discoveredProvider?.descriptor?.displayName
            ?.let(bidiFormatter::natural)
            ?.takeUnless { name -> name.isBlank() || name == sourceLabel }
        else -> null
    }
    val externalProviderIdentity = expectedProviderId
        ?.takeIf {
            editorSource == DataSource.Provider &&
                (
                    providerSource?.executionKind ==
                        SubscriptionProviderExecutionKind.EXTERNAL ||
                        discoveredProvider?.executionKind ==
                        SubscriptionProviderExecutionKind.EXTERNAL
                    )
        }
        ?.let(bidiFormatter::ltr)
    val sourceSupporting = externalProviderIdentity?.let { stableProviderId ->
        stringResource(
            string.feat_setting_provider_choice_with_identifier,
            sourceSupportingName ?: sourceLabel,
            stableProviderId,
        )
    } ?: sourceSupportingName
    val sourceSupportingContentDescription =
        externalProviderIdentity?.let { stableProviderId ->
            stringResource(
                string.feat_setting_provider_choice_with_identifier_description,
                sourceSupportingName ?: sourceLabel,
                stableProviderId,
            )
        }
    val sourceIcon = when (editorSource) {
        DataSource.M3U -> Icons.Rounded.Link
        DataSource.EPG -> Icons.Rounded.DateRange
        DataSource.Xtream -> Icons.Rounded.Cloud
        DataSource.Provider -> Icons.Rounded.Extension
        else -> Icons.Rounded.Link
    }
    val showsLocalStorageOption = editorSource == DataSource.M3U
    val showsRemoteTvOption =
        remoteControl && editorSource in REMOTE_TV_SUBSCRIPTION_SOURCES
    val localFileReady =
        !properties.localStorageState.value || properties.uriState.value != Uri.EMPTY
    val editorInputReady = properties.titleState.value.isNotBlank() && when (editorSource) {
        DataSource.M3U -> if (properties.localStorageState.value) {
            localFileReady
        } else {
            properties.urlState.value.isNotBlank()
        }
        DataSource.EPG -> properties.epgState.value.isNotBlank()
        DataSource.Xtream ->
            properties.basicUrlState.value.isNotBlank() &&
                properties.usernameState.value.isNotBlank() &&
                properties.passwordState.value.isNotBlank()
        DataSource.Provider -> providerDiscoveryState.supports(matchingProviderForm)
        else -> false
    }

    LaunchedEffect(
        draftKey,
        sourceKey,
        reauthenticationPlaylistUrl,
        providerSource?.providerId,
        providerSource?.providerKind,
    ) {
        if (!isReauthentication) {
            onBeginSubscriptionDraft(draftKey, editorSource)
            if (providerSource != null) {
                onSelectSubscriptionProviderVariant(
                    providerSource.providerId.value,
                    providerSource.providerKind.value,
                )
            }
        } else if (ordinarySource != null) {
            properties.selectedState.value = ordinarySource
        }
        draftReady = true
    }
    LaunchedEffect(editorSource) {
        if (editorSource != DataSource.M3U) {
            properties.localStorageState.value = false
        }
        if (editorSource !in REMOTE_TV_SUBSCRIPTION_SOURCES) {
            properties.forTvState.value = false
        }
    }

    if (!draftReady) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(32.dp)
                    .clearAndSetSemantics {
                        stateDescription = loadingStateDescription
                    },
                strokeWidth = 3.dp,
            )
        }
        return
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(spacing.small),
        contentPadding = contentPadding + PaddingValues(vertical = spacing.medium),
        modifier = modifier
            .testTag("playlist-editor:$sourceKey")
            .imePadding()
    ) {
        item(key = "source") {
            SubscriptionEditorPageContent {
                SubscriptionSourceSummary(
                    label = sourceLabel,
                    supporting = sourceSupporting,
                    supportingContentDescription =
                        sourceSupportingContentDescription,
                    icon = sourceIcon,
                )
            }
        }

        item(key = "form") {
            SubscriptionEditorPageContent {
                SubscriptionEditorSection {
                    when (editorSource) {
                        DataSource.M3U -> M3UInputContent(
                            enabled = !operationInProgress,
                            showErrors = submissionAttempted,
                        )
                        DataSource.EPG -> EPGInputContent(
                            enabled = !operationInProgress,
                            showErrors = submissionAttempted,
                        )
                        DataSource.Xtream -> XtreamInputContent(
                            enabled = !operationInProgress,
                            showErrors = submissionAttempted,
                        )
                        DataSource.Provider -> DynamicProviderInputContent(
                            discoveryState = providerDiscoveryState,
                            form = matchingProviderForm,
                            onUpdateField = onUpdateSubscriptionProviderSetting,
                            onRetry = {
                                reauthenticationPlaylistUrl?.let(
                                    onRetryProviderReauthentication
                                ) ?: onRetryProviderDiscovery()
                            },
                            preparing = reauthenticationPlaylistUrl?.let(
                                providerOperationState::isReauthenticating
                            ) == true,
                            enabled = !operationInProgress,
                            showErrors = submissionAttempted,
                        )
                        else -> Unit
                    }
                }
            }
        }

        if (operationInProgress && !providerSubmissionInProgress) {
            item(key = "maintenance") {
                SubscriptionEditorPageContent {
                    PlaylistMaintenanceNotice()
                }
            }
        }

        if (showsLocalStorageOption || showsRemoteTvOption) {
            item(key = "options") {
                SubscriptionEditorPageContent {
                    SubscriptionEditorSection {
                        Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
                            if (showsLocalStorageOption) {
                                LocalStorageSwitch(
                                    checked = properties.localStorageState.value,
                                    onChanged = { properties.localStorageState.value = it },
                                    enabled = !properties.forTvState.value &&
                                        !operationInProgress,
                                )
                            }
                            if (showsRemoteTvOption) {
                                RemoteControlSubscribeSwitch(
                                    checked = properties.forTvState.value,
                                    onChanged = {
                                        properties.forTvState.value =
                                            !properties.forTvState.value
                                    },
                                    enabled = (
                                        editorSource != DataSource.M3U ||
                                            !properties.localStorageState.value
                                        ) && !operationInProgress,
                                )
                            }
                        }
                    }
                }
            }
        }
        item(key = "submit") {
            @SuppressLint("InlinedApi")
            val postNotificationPermission = rememberPermissionState(
                Manifest.permission.POST_NOTIFICATIONS
            )
            SubscriptionEditorPageContent {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("subscription-submit-action")
                            .semantics {
                                if (providerSubmissionInProgress) {
                                    liveRegion = LiveRegionMode.Polite
                                    stateDescription = loadingStateDescription
                                }
                            },
                        enabled = !operationInProgress && (
                            editorSource != DataSource.Provider ||
                                editorInputReady
                            ),
                        onClick = {
                            submissionAttempted = true
                            if (
                                editorSource != DataSource.Provider &&
                                !editorInputReady
                            ) {
                                return@Button
                            }
                            if (
                                (
                                    editorSource == DataSource.M3U ||
                                        editorSource == DataSource.Xtream
                                    ) &&
                                Build.VERSION.SDK_INT >=
                                    Build.VERSION_CODES.TIRAMISU &&
                                postNotificationPermission.status
                                    is PermissionStatus.Denied
                            ) {
                                postNotificationPermission.launchPermissionRequest()
                            }
                            val needsLocalFileGrant =
                                editorSource == DataSource.M3U &&
                                    properties.localStorageState.value &&
                                    editorInputReady
                            if (needsLocalFileGrant) {
                                val permissionTag =
                                    beginPersistedUriPermissionLease(
                                        helper.activityContext,
                                        properties.uriState.value,
                                    )
                                val accessPersisted = runCatching {
                                    helper.activityContext.contentResolver
                                        .takePersistableUriPermission(
                                            properties.uriState.value,
                                            Intent.FLAG_GRANT_READ_URI_PERMISSION,
                                        )
                                }.isSuccess
                                if (!accessPersisted) {
                                    abandonPersistedUriPermissionLease(
                                        helper.activityContext,
                                        permissionTag,
                                    )
                                    Toast.makeText(
                                        helper.activityContext,
                                        fileAccessFailure,
                                        Toast.LENGTH_LONG,
                                    ).show()
                                    return@Button
                                }
                            }
                            onSubscribe()
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
                                if (isReauthentication) {
                                    string.feat_setting_provider_reauthenticate
                                } else if (providerSubmissionInProgress) {
                                    string.feat_setting_label_subscribing
                                } else {
                                    string.feat_setting_label_subscribe
                                }
                            )
                        )
                    }
                    when {
                        editorSource == DataSource.Xtream ||
                            (
                                editorSource == DataSource.M3U &&
                                    !properties.localStorageState.value
                                ) -> {
                            FilledTonalIconButton(
                                enabled = !operationInProgress,
                                onClick = {
                                    coroutineScope.launch {
                                        val clipData = clipboard.getClipEntry()?.clipData
                                        val text = if (clipData != null && clipData.itemCount > 0) {
                                            clipData.getItemAt(0).coerceToText(context).toString()
                                        } else {
                                            ""
                                        }
                                        onClipboard(text)
                                    }
                                },
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ContentPaste,
                                    contentDescription = stringResource(
                                        string.feat_setting_label_parse_from_clipboard
                                    )
                                )
                            }
                        }

                        else -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun SubscriptionEditorPageContent(
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth(),
        ) {
            content()
        }
    }
}

@Composable
private fun SubscriptionSourceSummary(
    label: String,
    supporting: String?,
    supportingContentDescription: String?,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleLarge.copy(
                        textDirection = TextDirection.ContentOrLtr,
                    ),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                supporting?.let { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            textDirection = TextDirection.ContentOrLtr,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = supportingContentDescription?.let { description ->
                            Modifier.clearAndSetSemantics {
                                contentDescription = description
                            }
                        } ?: Modifier,
                    )
                }
            }
        }
    }
}

@Composable
private fun SubscriptionEditorSection(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content()
        }
    }
}

@Composable
internal fun ProviderReauthenticationCard(
    account: ProviderAccountSummary,
    inProgress: Boolean,
    enabled: Boolean,
    onReauthenticate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val bidiFormatter = rememberUiBidiFormatter()
    val loadingStateDescription = stringResource(string.ui_state_loading)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("provider-reauthentication"),
        shape = MaterialTheme.shapes.extraLarge,
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
                    modifier = Modifier.weight(1f),
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
                    .heightIn(min = 48.dp)
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
context(properties: SettingProperties)
private fun M3UInputContent(
    enabled: Boolean,
    showErrors: Boolean,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val context = LocalContext.current
    val titleError = stringResource(string.feat_setting_error_empty_title)
        .takeIf { showErrors && properties.titleState.value.isBlank() }
    val urlError = stringResource(string.feat_setting_error_blank_url)
        .takeIf {
            showErrors &&
                !properties.localStorageState.value &&
                properties.urlState.value.isBlank()
        }
    val fileError = stringResource(string.feat_setting_error_unselected_file)
        .takeIf {
            showErrors &&
                properties.localStorageState.value &&
                properties.uriState.value == Uri.EMPTY
        }
    LaunchedEffect(Unit) {
        properties.applyBenchmarkPlaylistPrefill(DebugBenchmarkSettings.from(context))
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.small)
    ) {
        PlaylistOutlinedTextField(
            value = properties.titleState.value,
            label = stringResource(string.feat_setting_placeholder_title),
            onValueChange = { properties.titleState.value = it },
            enabled = enabled,
            errorMessage = titleError,
            imeAction = ImeAction.Next,
            modifier = Modifier.fillMaxWidth()
        )
        Crossfade(
            targetState = properties.localStorageState.value,
            label = "url"
        ) { localStorage ->
            if (!localStorage) {
                PlaylistOutlinedTextField(
                    value = properties.urlState.value,
                    label = stringResource(string.feat_setting_placeholder_url),
                    onValueChange = { properties.urlState.value = it },
                    enabled = enabled,
                    errorMessage = urlError,
                    keyboardType = KeyboardType.Uri,
                    textDirection = TextDirection.Ltr,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.extraSmall)) {
                    LocalStorageButton(
                        titleState = properties.titleState,
                        uriState = properties.uriState,
                        enabled = enabled,
                    )
                    fileError?.let { message ->
                        PlaylistFieldError(message)
                    }
                }
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
private fun PlaylistOutlinedTextField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    errorMessage: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    textDirection: TextDirection = TextDirection.ContentOrLtr,
    imeAction: ImeAction = ImeAction.Done,
) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        isError = errorMessage != null,
        label = {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingText = errorMessage?.let { message ->
            {
                Text(text = message)
            }
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(
            onNext = {
                focusManager.moveFocus(FocusDirection.Down)
            },
            onDone = {
                focusManager.clearFocus()
            },
        ),
        visualTransformation = visualTransformation,
        textStyle = LocalTextStyle.current.copy(textDirection = textDirection),
        shape = MaterialTheme.shapes.large,
        modifier = modifier.semantics {
            errorMessage?.let { message -> error(message) }
        },
    )
}

@Composable
private fun PlaylistFieldError(
    message: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = modifier.semantics { error(message) },
    )
}

@Composable
private fun PlaylistMaintenanceNotice(
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(20.dp)
                    .clearAndSetSemantics {},
                strokeWidth = 2.dp,
            )
            Text(
                text = stringResource(
                    string.feat_setting_playlist_maintenance_in_progress
                ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .weight(1f)
                    .semantics {
                        liveRegion = LiveRegionMode.Polite
                    },
            )
        }
    }
}

@Composable
context(properties: SettingProperties)
private fun EPGInputContent(
    enabled: Boolean,
    showErrors: Boolean,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val titleError = stringResource(string.feat_setting_error_empty_epg_title)
        .takeIf { showErrors && properties.titleState.value.isBlank() }
    val epgError = stringResource(string.feat_setting_error_empty_epg)
        .takeIf { showErrors && properties.epgState.value.isBlank() }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.small)
    ) {
        PlaylistOutlinedTextField(
            value = properties.titleState.value,
            label = stringResource(string.feat_setting_placeholder_epg_title),
            onValueChange = { properties.titleState.value = it },
            enabled = enabled,
            errorMessage = titleError,
            imeAction = ImeAction.Next,
            modifier = Modifier.fillMaxWidth()
        )
        PlaylistOutlinedTextField(
            value = properties.epgState.value,
            label = stringResource(string.feat_setting_placeholder_epg),
            onValueChange = { properties.epgState.value = it },
            enabled = enabled,
            errorMessage = epgError,
            keyboardType = KeyboardType.Uri,
            textDirection = TextDirection.Ltr,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
context(properties: SettingProperties)
private fun XtreamInputContent(
    enabled: Boolean,
    showErrors: Boolean,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val titleError = stringResource(string.feat_setting_error_empty_title)
        .takeIf { showErrors && properties.titleState.value.isBlank() }
    val urlError = stringResource(string.feat_setting_error_blank_url)
        .takeIf { showErrors && properties.basicUrlState.value.isBlank() }
    val requiredError = stringResource(string.feat_setting_provider_error_required)
    val usernameError = requiredError.takeIf {
        showErrors && properties.usernameState.value.isBlank()
    }
    val passwordError = requiredError.takeIf {
        showErrors && properties.passwordState.value.isBlank()
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.small)
    ) {
        PlaylistOutlinedTextField(
            value = properties.titleState.value,
            label = stringResource(string.feat_setting_placeholder_title),
            onValueChange = { properties.titleState.value = it },
            enabled = enabled,
            errorMessage = titleError,
            imeAction = ImeAction.Next,
            modifier = Modifier.fillMaxWidth()
        )
        PlaylistOutlinedTextField(
            value = properties.basicUrlState.value,
            label = stringResource(string.feat_setting_placeholder_basic_url),
            onValueChange = { properties.basicUrlState.value = it },
            enabled = enabled,
            errorMessage = urlError,
            keyboardType = KeyboardType.Uri,
            textDirection = TextDirection.Ltr,
            imeAction = ImeAction.Next,
            modifier = Modifier.fillMaxWidth()
        )
        PlaylistOutlinedTextField(
            value = properties.usernameState.value,
            label = stringResource(string.feat_setting_placeholder_username),
            onValueChange = { properties.usernameState.value = it },
            enabled = enabled,
            errorMessage = usernameError,
            imeAction = ImeAction.Next,
            modifier = Modifier.fillMaxWidth()
        )
        PlaylistOutlinedTextField(
            value = properties.passwordState.value,
            label = stringResource(string.feat_setting_placeholder_password),
            onValueChange = { properties.passwordState.value = it },
            enabled = enabled,
            errorMessage = passwordError,
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
    preparing: Boolean,
    enabled: Boolean,
    showErrors: Boolean,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val bidiFormatter = rememberUiBidiFormatter()
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        PlaylistOutlinedTextField(
            value = properties.titleState.value,
            label = stringResource(string.feat_setting_placeholder_title),
            onValueChange = { properties.titleState.value = it },
            enabled = enabled,
            errorMessage = stringResource(string.feat_setting_error_empty_title)
                .takeIf { showErrors && properties.titleState.value.isBlank() },
            imeAction = if (form?.fields?.isNotEmpty() == true) {
                ImeAction.Next
            } else {
                ImeAction.Done
            },
            modifier = Modifier.fillMaxWidth(),
        )
        when {
            discoveryState is ProviderDiscoveryState.Loading || preparing -> {
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

            form == null -> {
                ProviderDiscoveryRetryNotice(
                    message = stringResource(
                        string.feat_setting_provider_selected_unavailable
                    ),
                    onRetry = onRetry,
                    enabled = enabled,
                    testTag = "provider-selected-unavailable",
                )
            }
        }
        form?.fields?.forEachIndexed { index, field ->
            ProviderFormField(
                field = field,
                bidiFormatter = bidiFormatter,
                enabled = enabled,
                isLast = index == form.fields.lastIndex,
                onUpdate = { value -> onUpdateField(field.definition.key, value) },
            )
        }
    }
}

@Composable
internal fun ProviderDiscoveryLoadingNotice(
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    Row(
        modifier = modifier.testTag("provider-discovery-loading"),
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
internal fun ProviderDiscoveryRetryNotice(
    message: String,
    onRetry: () -> Unit,
    enabled: Boolean,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    Column(
        modifier = modifier.testTag(testTag),
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
            modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("provider-discovery-retry"),
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
    isLast: Boolean,
    onUpdate: (String?) -> Unit,
) {
    val definition = field.definition
    val spacing = LocalSpacing.current
    val focusManager = LocalFocusManager.current
    val errorMessage = field.error?.let { stringResource(it.messageResource()) }
    val requiredDescription =
        stringResource(string.feat_setting_provider_error_required)
    Column(
        modifier = Modifier.testTag("provider-field:${definition.key}"),
        verticalArrangement = Arrangement.spacedBy(spacing.extraSmall),
    ) {
        val displayLabel = bidiFormatter.natural(definition.label)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.small),
            verticalArrangement = Arrangement.spacedBy(spacing.extraSmall),
        ) {
            Text(
                text = displayLabel,
                style = MaterialTheme.typography.labelLarge.copy(
                    textDirection = TextDirection.ContentOrLtr,
                ),
            )
            if (definition.required) {
                Text(
                    text = requiredDescription,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        definition.description?.let { description ->
            Text(
                bidiFormatter.natural(
                    value = description,
                    maximumCharacters = MAX_PROVIDER_DESCRIPTION_LENGTH,
                ),
                style = MaterialTheme.typography.bodySmall.copy(
                    textDirection = TextDirection.ContentOrLtr,
                ),
            )
        }
        when (definition.type) {
            ExtensionSettingType.TEXT,
            ExtensionSettingType.NUMBER,
            ExtensionSettingType.SECRET -> {
                val textDirection = if (
                    definition.type == ExtensionSettingType.NUMBER ||
                        definition.type == ExtensionSettingType.SECRET ||
                        definition.networkOrigin ||
                        definition.key.contains("url", ignoreCase = true) ||
                        definition.key.contains("origin", ignoreCase = true) ||
                        definition.key.contains("address", ignoreCase = true)
                ) {
                    TextDirection.Ltr
                } else {
                    TextDirection.ContentOrLtr
                }
                OutlinedTextField(
                    value = field.value.orEmpty(),
                    onValueChange = onUpdate,
                    enabled = enabled,
                    isError = errorMessage != null,
                    singleLine = true,
                    minLines = 1,
                    maxLines = 1,
                    textStyle = LocalTextStyle.current.copy(
                        textDirection = textDirection,
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = when (definition.type) {
                            ExtensionSettingType.NUMBER -> KeyboardType.Decimal
                            ExtensionSettingType.SECRET -> KeyboardType.Password
                            else -> KeyboardType.Text
                        },
                        imeAction = when {
                            isLast -> ImeAction.Done
                            else -> ImeAction.Next
                        },
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = {
                            focusManager.moveFocus(FocusDirection.Down)
                        },
                        onDone = {
                            focusManager.clearFocus()
                        },
                    ),
                    visualTransformation =
                        if (definition.type == ExtensionSettingType.SECRET) {
                            PasswordVisualTransformation()
                        } else {
                            VisualTransformation.None
                        },
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = displayLabel
                            if (definition.required) {
                                stateDescription = requiredDescription
                            }
                            if (errorMessage != null) {
                                error(errorMessage)
                            }
                        },
                )
            }

            ExtensionSettingType.BOOLEAN -> FlowRow(
                modifier = Modifier
                    .selectableGroup()
                    .providerChoiceGroupSemantics(
                        fieldLabel = displayLabel,
                        requiredDescription = requiredDescription.takeIf {
                            definition.required
                        },
                        errorMessage = errorMessage,
                    ),
                horizontalArrangement = Arrangement.spacedBy(spacing.small),
                verticalArrangement = Arrangement.spacedBy(spacing.small),
            ) {
                ProviderResetChoice(
                    field = field,
                    fieldLabel = displayLabel,
                    enabled = enabled,
                    onUpdate = onUpdate,
                )
                ProviderChoiceButton(
                    fieldLabel = displayLabel,
                    selected = field.value == "true" && !field.isUsingDefault,
                    enabled = enabled,
                    onClick = { onUpdate("true") },
                    text = stringResource(string.feat_setting_provider_value_true),
                )
                ProviderChoiceButton(
                    fieldLabel = displayLabel,
                    selected = field.value == "false" && !field.isUsingDefault,
                    enabled = enabled,
                    onClick = { onUpdate("false") },
                    text = stringResource(string.feat_setting_provider_value_false),
                )
            }

            ExtensionSettingType.SINGLE_CHOICE -> FlowRow(
                modifier = Modifier
                    .selectableGroup()
                    .providerChoiceGroupSemantics(
                        fieldLabel = displayLabel,
                        requiredDescription = requiredDescription.takeIf {
                            definition.required
                        },
                        errorMessage = errorMessage,
                    ),
                horizontalArrangement = Arrangement.spacedBy(spacing.small),
                verticalArrangement = Arrangement.spacedBy(spacing.small),
            ) {
                ProviderResetChoice(
                    field = field,
                    fieldLabel = displayLabel,
                    enabled = enabled,
                    onUpdate = onUpdate,
                )
                definition.choices.forEach { choice ->
                    ProviderChoiceButton(
                        fieldLabel = displayLabel,
                        selected = field.value == choice.value && !field.isUsingDefault,
                        enabled = enabled,
                        onClick = { onUpdate(choice.value) },
                        text = bidiFormatter.natural(choice.label),
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
    fieldLabel: String,
    enabled: Boolean,
    onUpdate: (String?) -> Unit,
) {
    if (field.definition.defaultValue != null || !field.definition.required) {
        ProviderChoiceButton(
            fieldLabel = fieldLabel,
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
    fieldLabel: String,
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
    val choiceDescription = stringResource(
        string.feat_setting_extension_choice_field_description,
        text,
        fieldLabel,
    )
    Surface(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .alpha(if (enabled) 1f else 0.38f)
            .semantics {
                role = Role.RadioButton
                contentDescription = choiceDescription
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

private fun Modifier.providerChoiceGroupSemantics(
    fieldLabel: String,
    requiredDescription: String?,
    errorMessage: String?,
): Modifier = semantics {
    contentDescription = fieldLabel
    requiredDescription?.let { description ->
        stateDescription = description
    }
    errorMessage?.let { message ->
        error(message)
    }
}

private fun ProviderSettingFieldError.messageResource(): Int = when (this) {
    ProviderSettingFieldError.REQUIRED -> string.feat_setting_provider_error_required
    ProviderSettingFieldError.TOO_LONG -> string.feat_setting_provider_error_too_long
    ProviderSettingFieldError.UNSAFE_VALUE -> string.feat_setting_provider_error_unsafe_value
    ProviderSettingFieldError.INVALID_NUMBER -> string.feat_setting_provider_error_number
    ProviderSettingFieldError.INVALID_BOOLEAN -> string.feat_setting_provider_error_boolean
    ProviderSettingFieldError.INVALID_CHOICE -> string.feat_setting_provider_error_choice
}

private val REMOTE_TV_SUBSCRIPTION_SOURCES = setOf(
    DataSource.M3U,
    DataSource.EPG,
    DataSource.Xtream,
)

private const val MAX_PROVIDER_DESCRIPTION_LENGTH = 1_024

internal const val PROVIDER_SOURCE_PREFIX = "provider:"

internal fun DataSource.subscriptionSelectionKey(): String = "data-source:$value"

internal fun ProviderSubscriptionSource.subscriptionSelectionKey(): String =
    providerSourceSelectionKey(providerId.value, providerKind.value)

internal fun providerSourceSelectionKey(
    providerId: String,
    providerKind: String,
): String = "$PROVIDER_SOURCE_PREFIX$providerId:$providerKind"

internal fun ordinarySubscriptionSourceOrNull(sourceKey: String): DataSource? =
    REMOTE_TV_SUBSCRIPTION_SOURCES.firstOrNull { source ->
        source.subscriptionSelectionKey() == sourceKey
    }

@Composable
private fun Warning(
    text: String,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.small),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp),
        ) {
            Icon(imageVector = Icons.Rounded.Warning, contentDescription = null)
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
