package com.m3u.smartphone.ui.business.setting.fragments

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.m3u.business.setting.ExtensionPluginDiscoveryState
import com.m3u.business.setting.ExtensionPluginOperation
import com.m3u.business.setting.ExtensionPluginOperationState
import com.m3u.core.foundation.architecture.preferences.PreferencesKeys
import com.m3u.core.foundation.architecture.preferences.mutablePreferenceOf
import com.m3u.data.repository.plugin.InstalledPlugin
import com.m3u.data.repository.plugin.PluginAuthorizationToken
import com.m3u.extension.api.ExtensionState
import com.m3u.i18n.R.string
import com.m3u.smartphone.ui.material.ktx.plus
import java.text.NumberFormat

private val ExtensionPageMaxWidth = 640.dp

@Composable
internal fun ExtensionPluginListScreen(
    state: ExtensionPluginDiscoveryState,
    operationState: ExtensionPluginOperationState,
    onRefresh: () -> Unit,
    onOpenDetails: (packageName: String, serviceName: String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    var externalExtensionsEnabled by mutablePreferenceOf(PreferencesKeys.EXTERNAL_EXTENSIONS)
    val plugins = state.plugins
    val operationRunning = operationState is ExtensionPluginOperationState.Running
    val refreshing = (operationState as? ExtensionPluginOperationState.Running)
        ?.operation == ExtensionPluginOperation.Refresh
    val enabledState = stringResource(string.feat_setting_extension_state_enabled)
    val disabledState = stringResource(string.feat_setting_extension_state_disabled)
    val loadingDescription = stringResource(string.feat_setting_extension_loading)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("extension-list"),
        contentPadding = contentPadding + PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        item(key = "external-extension-toggle") {
            ExtensionPageContent {
                ListItem(
                    headlineContent = {
                        Text(stringResource(string.feat_setting_external_extensions))
                    },
                    supportingContent = {
                        Text(
                            stringResource(
                                string.feat_setting_external_extensions_description
                            )
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Rounded.Extension,
                            contentDescription = null,
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = externalExtensionsEnabled,
                            onCheckedChange = null,
                            enabled = !operationRunning,
                            modifier = Modifier.clearAndSetSemantics {},
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("extension-feature-toggle")
                        .semantics(mergeDescendants = true) {
                            stateDescription = if (externalExtensionsEnabled) {
                                enabledState
                            } else {
                                disabledState
                            }
                        }
                        .toggleable(
                            value = externalExtensionsEnabled,
                            enabled = !operationRunning,
                            role = Role.Switch,
                            onValueChange = { enabled ->
                                externalExtensionsEnabled = enabled
                            },
                        ),
                )
                HorizontalDivider()
            }
        }

        item(key = "extension-list-heading") {
            ExtensionPageContent {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 20.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(string.feat_setting_extension_on_device),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp)
                            .semantics { heading() },
                    )
                    IconButton(
                        onClick = onRefresh,
                        enabled = externalExtensionsEnabled && !operationRunning,
                        modifier = Modifier.testTag("extension-refresh"),
                    ) {
                        if (refreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(24.dp)
                                    .semantics {
                                        contentDescription = loadingDescription
                                    },
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = stringResource(string.ui_action_refresh),
                            )
                        }
                    }
                }
            }
        }

        if (externalExtensionsEnabled && state is ExtensionPluginDiscoveryState.Error) {
            item(key = "extensions-error") {
                ExtensionPageContent {
                    ExtensionWarning(
                        message = stringResource(
                            string.feat_setting_extension_operation_failed
                        ),
                        modifier = Modifier.testTag("extension-list-error"),
                    )
                }
            }
        }

        when {
            !externalExtensionsEnabled -> {
                item(key = "extensions-disabled") {
                    ExtensionPageContent {
                        ExtensionEmptyState(
                            text = stringResource(
                                string.feat_setting_extension_enable_external_hint
                            ),
                        )
                    }
                }
            }

            state is ExtensionPluginDiscoveryState.Loading && plugins.isEmpty() -> {
                item(key = "extensions-loading") {
                    ExtensionPageContent {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp)
                                .testTag("extension-list-loading"),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.semantics {
                                    contentDescription = loadingDescription
                                },
                            )
                        }
                    }
                }
            }

            plugins.isEmpty() && state !is ExtensionPluginDiscoveryState.Error -> {
                item(key = "extensions-empty") {
                    ExtensionPageContent {
                        ExtensionEmptyState(
                            text = stringResource(string.feat_setting_extension_no_plugins),
                        )
                    }
                }
            }

            else -> {
                items(
                    items = plugins,
                    key = { plugin -> plugin.stableKey },
                ) { plugin ->
                    ExtensionPageContent {
                        ExtensionPluginListItem(
                            plugin = plugin,
                            onClick = {
                                onOpenDetails(plugin.packageName, plugin.serviceName)
                            },
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ExtensionPluginListItem(
    plugin: InstalledPlugin,
    onClick: () -> Unit,
) {
    val bidiFormatter = rememberUiBidiFormatter()
    val hasWarning = plugin.hasVisibleWarning
    val stateLabel = extensionStateLabel(plugin.state)
    val warningDescription = extensionWarningMessages(
        plugin = plugin,
        unapprovedNetworkOrigins = plugin.networkOrigins - plugin.approvedNetworkOrigins,
        bidiFormatter = bidiFormatter,
    ).joinToString(separator = "\n")
    val supportingText = buildList {
        plugin.developer
            ?.takeIf(String::isNotBlank)
            ?.let(bidiFormatter::natural)
            ?.let(::add)
        plugin.version
            ?.takeIf(String::isNotBlank)
            ?.let { version -> bidiFormatter.ltr("v$version") }
            ?.let(::add)
    }.joinToString(separator = " · ")

    ListItem(
        headlineContent = {
            Text(
                plugin.displayName
                    ?.takeIf(String::isNotBlank)
                    ?.let(bidiFormatter::natural)
                    ?: bidiFormatter.ltr(plugin.packageName),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (supportingText.isNotEmpty()) {
                    Text(
                        text = supportingText,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = stateLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (hasWarning) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        },
        leadingContent = {
            ExtensionApplicationIcon(
                plugin = plugin,
                size = 44.dp,
                fallbackIconSize = 22.dp,
                warningBadgeSize = 18.dp,
            )
        },
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
            )
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(
                role = Role.Button,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) {
                stateDescription = stateLabel
                if (warningDescription.isNotEmpty()) {
                    error(warningDescription)
                }
            }
            .testTag("extension-plugin-list-item:${plugin.stableKey}"),
    )
}

@Composable
internal fun ExtensionPluginDetailScreen(
    plugin: InstalledPlugin?,
    operationState: ExtensionPluginOperationState,
    onOpenAuthorization: (reauthorize: Boolean) -> Unit,
    onOpenSettings: (extensionId: String) -> Unit,
    onDisable: (extensionId: String) -> Unit,
    onRevoke: (packageName: String, serviceName: String, extensionId: String?) -> Unit,
    onClearData: (packageName: String, serviceName: String, extensionId: String?) -> Unit,
    onExportDiagnostics: (extensionId: String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    if (plugin == null) {
        ExtensionUnavailableScreen(
            modifier = modifier,
            contentPadding = contentPadding,
        )
        return
    }

    val bidiFormatter = rememberUiBidiFormatter()
    val actions = plugin.actionAvailability()
    val unapprovedNetworkOrigins = plugin.networkOrigins - plugin.approvedNetworkOrigins
    val reauthorizationIsPrimary = actions.reauthorize &&
        (plugin.signatureChanged || unapprovedNetworkOrigins.isNotEmpty())
    val operationRunning = operationState is ExtensionPluginOperationState.Running
    val operationTargetsPlugin = operationState.targets(plugin)
    val operationDescription = stringResource(string.feat_setting_extension_loading)
    val extensionId = plugin.extensionId
    var pendingRevoke by remember { mutableStateOf(false) }
    var pendingClear by remember { mutableStateOf(false) }
    var capabilitiesExpanded by rememberSaveable(plugin.stableKey) {
        mutableStateOf(false)
    }
    var networkOriginsExpanded by rememberSaveable(plugin.stableKey) {
        mutableStateOf(false)
    }
    var technicalIdentityExpanded by rememberSaveable(plugin.stableKey) {
        mutableStateOf(false)
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("extension-plugin-detail:${plugin.stableKey}"),
            contentPadding =
                contentPadding + PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item(key = "identity") {
                ExtensionPageContent {
                    ExtensionIdentityHeader(
                        plugin = plugin,
                        bidiFormatter = bidiFormatter,
                    )
                }
            }

            if (plugin.hasVisibleWarning) {
                item(key = "warnings") {
                    ExtensionPageContent {
                        ExtensionWarningSummary(
                            plugin = plugin,
                            unapprovedNetworkOrigins = unapprovedNetworkOrigins,
                            bidiFormatter = bidiFormatter,
                        )
                    }
                }
            }

            if (actions.hasControlActions) {
                item(key = "actions") {
                    ExtensionPageContent {
                        ExtensionPluginActions(
                            plugin = plugin,
                            reauthorizationIsPrimary = reauthorizationIsPrimary,
                            onOpenAuthorization = onOpenAuthorization,
                            onOpenSettings = onOpenSettings,
                            onDisable = onDisable,
                            enabled = !operationRunning,
                        )
                    }
                }
            }

            if (
                plugin.capabilityPermissions.isNotEmpty() ||
                plugin.networkOrigins.isNotEmpty()
            ) {
                item(key = "access") {
                    ExtensionPageContent {
                        ExtensionSection(
                            title = stringResource(string.feat_setting_extension_access),
                        ) {
                            ExtensionAccessOverview(
                                plugin = plugin,
                                capabilitiesExpanded = capabilitiesExpanded,
                                onCapabilitiesExpandedChange = {
                                    capabilitiesExpanded = it
                                },
                                networkOriginsExpanded = networkOriginsExpanded,
                                onNetworkOriginsExpandedChange = {
                                    networkOriginsExpanded = it
                                },
                                bidiFormatter = bidiFormatter,
                            )
                        }
                    }
                }
            }

            item(key = "support-and-details") {
                ExtensionPageContent {
                    ExtensionSection(
                        title = stringResource(
                            string.feat_setting_extension_support_and_details
                        ),
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.extraLarge,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                        ) {
                            Column {
                                if (actions.exportDiagnostics && extensionId != null) {
                                    ExtensionActionRow(
                                        label = stringResource(
                                            string.feat_setting_extension_export_diagnostics
                                        ),
                                        icon = Icons.Rounded.BugReport,
                                        onClick = { onExportDiagnostics(extensionId) },
                                        enabled = !operationRunning,
                                        testTag = plugin.actionTestTag(
                                            "export-diagnostics"
                                        ),
                                        showLeadingContainer = true,
                                    )
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )
                                }
                                ExtensionDisclosureHeader(
                                    title = stringResource(
                                        string.feat_setting_extension_identity
                                    ),
                                    summary = stringResource(
                                        string.feat_setting_extension_identity_summary
                                    ),
                                    icon = Icons.Rounded.Extension,
                                    expanded = technicalIdentityExpanded,
                                    onExpandedChange = {
                                        technicalIdentityExpanded = it
                                    },
                                    testTag =
                                        "extension-technical-identity-disclosure",
                                    titleIsHeading = true,
                                )
                                AnimatedVisibility(
                                    visible = technicalIdentityExpanded
                                ) {
                                    ExtensionTechnicalIdentity(
                                        plugin = plugin,
                                        bidiFormatter = bidiFormatter,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (actions.clearData || actions.revoke) {
                item(key = "data-actions") {
                    ExtensionPageContent {
                        ExtensionSection(
                            title = stringResource(
                                string.feat_setting_extension_data_and_trust
                            ),
                        ) {
                            Surface(
                                shape = MaterialTheme.shapes.extraLarge,
                                color = MaterialTheme.colorScheme.surfaceContainer,
                            ) {
                                ExtensionPluginDataActions(
                                    plugin = plugin,
                                    onClearData = { pendingClear = true },
                                    onRevoke = { pendingRevoke = true },
                                    enabled = !operationRunning,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (operationTargetsPlugin) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .widthIn(max = ExtensionPageMaxWidth)
                        .fillMaxWidth()
                        .testTag("extension-operation-progress")
                        .semantics {
                            contentDescription = operationDescription
                            liveRegion = LiveRegionMode.Polite
                        },
                )
            }
        }
    }

    if (pendingRevoke) {
        ExtensionDataRemovalDialog(
            plugin = plugin,
            title = stringResource(string.feat_setting_extension_forget_title),
            body = stringResource(string.feat_setting_extension_forget_body),
            confirmLabel = stringResource(string.feat_setting_extension_revoke),
            dialogTag = "extension-revoke-dialog",
            confirmTag = "extension-revoke-confirm",
            onDismiss = { pendingRevoke = false },
            onConfirm = {
                pendingRevoke = false
                onRevoke(plugin.packageName, plugin.serviceName, plugin.extensionId)
            },
        )
    }
    if (pendingClear) {
        ExtensionDataRemovalDialog(
            plugin = plugin,
            title = stringResource(string.feat_setting_extension_clear_data_title),
            body = stringResource(string.feat_setting_extension_clear_data_body),
            confirmLabel = stringResource(string.feat_setting_extension_clear_data),
            dialogTag = "extension-clear-data-dialog",
            confirmTag = "extension-clear-data-confirm",
            onDismiss = { pendingClear = false },
            onConfirm = {
                pendingClear = false
                onClearData(plugin.packageName, plugin.serviceName, plugin.extensionId)
            },
        )
    }
}

@Composable
private fun ExtensionPluginActions(
    plugin: InstalledPlugin,
    reauthorizationIsPrimary: Boolean,
    onOpenAuthorization: (reauthorize: Boolean) -> Unit,
    onOpenSettings: (extensionId: String) -> Unit,
    onDisable: (extensionId: String) -> Unit,
    enabled: Boolean,
) {
    val actions = plugin.actionAvailability()
    val extensionId = plugin.extensionId
    val settingsLabel = stringResource(string.feat_setting_extension_settings)
    val reauthorizeLabel = stringResource(string.feat_setting_extension_reauthorize)
    val disableLabel = stringResource(string.feat_setting_extension_disable)
    val stackedActions = LocalDensity.current.fontScale >= 1.5f
    val groupedActions = buildList {
        if (actions.settings && extensionId != null) {
            add(
                ExtensionActionItem(
                    label = settingsLabel,
                    icon = Icons.Rounded.Settings,
                    onClick = { onOpenSettings(extensionId) },
                    enabled = enabled,
                    testTag = plugin.actionTestTag("settings"),
                    prominent = true,
                )
            )
        }
        if (actions.reauthorize && !reauthorizationIsPrimary) {
            add(
                ExtensionActionItem(
                    label = reauthorizeLabel,
                    icon = Icons.Rounded.Security,
                    onClick = { onOpenAuthorization(true) },
                    enabled = enabled,
                    testTag = plugin.actionTestTag("reauthorize"),
                )
            )
        }
        if (actions.disable && extensionId != null) {
            add(
                ExtensionActionItem(
                    label = disableLabel,
                    icon = Icons.Rounded.PowerSettingsNew,
                    onClick = { onDisable(extensionId) },
                    enabled = enabled,
                    testTag = plugin.actionTestTag("disable"),
                )
            )
        }
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when {
            actions.enable -> {
                Button(
                    onClick = { onOpenAuthorization(false) },
                    enabled = enabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag(plugin.actionTestTag("enable")),
                ) {
                    Text(stringResource(string.feat_setting_extension_enable))
                }
            }

            reauthorizationIsPrimary -> {
                Button(
                    onClick = { onOpenAuthorization(true) },
                    enabled = enabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag(plugin.actionTestTag("reauthorize")),
                ) {
                    Text(reauthorizeLabel)
                }
            }
        }

        if (groupedActions.isNotEmpty()) {
            ExtensionActionGroup(
                actions = groupedActions,
                stacked = stackedActions,
            )
        }
    }
}

private data class ExtensionActionItem(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val enabled: Boolean,
    val testTag: String,
    val prominent: Boolean = false,
)

@Composable
private fun ExtensionActionGroup(
    actions: List<ExtensionActionItem>,
    stacked: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        if (stacked) {
            Column {
                actions.forEachIndexed { index, action ->
                    ExtensionActionRow(
                        label = action.label,
                        icon = action.icon,
                        onClick = action.onClick,
                        enabled = action.enabled,
                        testTag = action.testTag,
                        prominent = action.prominent,
                    )
                    if (index != actions.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                actions.forEachIndexed { index, action ->
                    ExtensionCompactAction(
                        action = action,
                        modifier = Modifier.weight(1f),
                    )
                    if (index != actions.lastIndex) {
                        VerticalDivider(
                            modifier = Modifier.height(64.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExtensionCompactAction(
    action: ExtensionActionItem,
    modifier: Modifier = Modifier,
) {
    val contentColor = if (action.prominent) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = modifier
            .heightIn(min = 104.dp)
            .alpha(if (action.enabled) 1f else 0.38f)
            .clickable(
                enabled = action.enabled,
                role = Role.Button,
                onClick = action.onClick,
            )
            .semantics(mergeDescendants = true) {}
            .testTag(action.testTag)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(
            space = 8.dp,
            alignment = Alignment.CenterVertically,
        ),
    ) {
        Icon(
            imageVector = action.icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = action.label,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ExtensionActionRow(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean,
    testTag: String,
    showsNavigation: Boolean = false,
    destructive: Boolean = false,
    prominent: Boolean = false,
    showLeadingContainer: Boolean = false,
) {
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        destructive -> MaterialTheme.colorScheme.error
        prominent -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    ListItem(
        headlineContent = {
            Text(
                text = label,
                color = contentColor,
            )
        },
        leadingContent = {
            if (showLeadingContainer) {
                ExtensionLeadingIcon(
                    icon = icon,
                    contentColor = contentColor,
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                )
            }
        },
        trailingContent = if (showsNavigation) {
            {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = contentColor,
                )
            }
        } else {
            null
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(MaterialTheme.shapes.large)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) {}
            .testTag(testTag),
    )
}

@Composable
private fun ExtensionPluginDataActions(
    plugin: InstalledPlugin,
    onClearData: () -> Unit,
    onRevoke: () -> Unit,
    enabled: Boolean,
) {
    val actions = plugin.actionAvailability()
    Column(modifier = Modifier.fillMaxWidth()) {
        if (actions.clearData) {
            ExtensionActionRow(
                label = stringResource(string.feat_setting_extension_clear_data),
                icon = Icons.Rounded.DeleteOutline,
                onClick = onClearData,
                enabled = enabled,
                testTag = plugin.actionTestTag("clear-data"),
                destructive = true,
            )
        }
        if (actions.clearData && actions.revoke) {
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
        }
        if (actions.revoke) {
            ExtensionActionRow(
                label = stringResource(string.feat_setting_extension_revoke),
                icon = Icons.Rounded.Security,
                onClick = onRevoke,
                enabled = enabled,
                testTag = plugin.actionTestTag("revoke"),
                destructive = true,
            )
        }
    }
}

@Composable
internal fun ExtensionPluginAuthorizationScreen(
    plugin: InstalledPlugin?,
    reauthorize: Boolean,
    onAuthorize: (
        packageName: String,
        serviceName: String,
        authorizationToken: PluginAuthorizationToken,
        reauthorize: Boolean,
    ) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    if (plugin == null) {
        ExtensionUnavailableScreen(
            modifier = modifier,
            contentPadding = contentPadding,
        )
        return
    }

    val bidiFormatter = rememberUiBidiFormatter()
    val confirmLabel = stringResource(
        if (reauthorize) {
            string.feat_setting_extension_reauthorize
        } else {
            string.feat_setting_extension_enable
        }
    )
    val loadingDescription = stringResource(string.feat_setting_extension_loading)
    val authorizationToken = plugin.authorizationToken
    var submitted by rememberSaveable(plugin.stableKey, reauthorize) { mutableStateOf(false) }
    val authorizationLoading = authorizationToken == null || submitted
    var identityExpanded by rememberSaveable(plugin.stableKey, reauthorize) {
        mutableStateOf(false)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("extension-authorization"),
        contentPadding = contentPadding + PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(key = "identity") {
            ExtensionPageContent {
                ExtensionAuthorizationIdentity(
                    plugin = plugin,
                    bidiFormatter = bidiFormatter,
                )
            }
        }

        plugin.previousCertificateSha256?.let { previousCertificate ->
            item(key = "certificate-change") {
                ExtensionPageContent {
                    ExtensionWarning(
                        stringResource(
                            string.feat_setting_extension_certificate_repin,
                            bidiFormatter.ltr(
                                previousCertificate.shortCertificateFingerprint()
                            ),
                            bidiFormatter.ltr(
                                plugin.certificateSha256.shortCertificateFingerprint()
                            ),
                        )
                    )
                }
            }
        }

        if (plugin.capabilityPermissions.isNotEmpty()) {
            item(key = "capabilities") {
                ExtensionPageContent {
                    ExtensionSection(
                        title = stringResource(
                            string.feat_setting_extension_requested_capabilities
                        ),
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.extraLarge,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                        ) {
                            ExtensionCapabilityList(
                                plugin = plugin,
                                bidiFormatter = bidiFormatter,
                                reviewingAuthorization = true,
                                showTopDivider = false,
                            )
                        }
                    }
                }
            }
        }

        if (
            plugin.networkOrigins.isNotEmpty() ||
            plugin.networkOriginSettingFields.isNotEmpty()
        ) {
            item(key = "origins") {
                ExtensionPageContent {
                    ExtensionSection(
                        title = stringResource(string.feat_setting_extension_network_origins),
                    ) {
                        ExtensionAuthorizationNetworkOrigins(
                            plugin = plugin,
                            bidiFormatter = bidiFormatter,
                        )
                    }
                }
            }
        }

        item(key = "identity-details") {
            ExtensionPageContent {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Column {
                        ExtensionDisclosureHeader(
                            title = stringResource(string.feat_setting_extension_identity),
                            summary = stringResource(
                                string.feat_setting_extension_identity_summary
                            ),
                            icon = Icons.Rounded.Extension,
                            expanded = identityExpanded,
                            onExpandedChange = { identityExpanded = it },
                            testTag = "extension-authorization-identity-disclosure",
                        )
                        AnimatedVisibility(visible = identityExpanded) {
                            Column {
                                ExtensionTechnicalIdentity(
                                    plugin = plugin,
                                    bidiFormatter = bidiFormatter,
                                )
                                plugin.previousCertificateSha256?.let {
                                    previousCertificate ->
                                    ExtensionWarning(
                                        message = stringResource(
                                            string.feat_setting_extension_certificate_repin,
                                            bidiFormatter.ltr(
                                                previousCertificate
                                                    .chunked(16)
                                                    .joinToString(" ")
                                            ),
                                            bidiFormatter.ltr(
                                                plugin.certificateSha256
                                                    .chunked(16)
                                                    .joinToString(" ")
                                            ),
                                        ),
                                        modifier = Modifier.padding(16.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item(key = "actions") {
            ExtensionPageContent {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(
                        space = 8.dp,
                        alignment = Alignment.End,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        onClick = onCancel,
                        enabled = !submitted,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("extension-authorization-cancel"),
                    ) {
                        Text(stringResource(android.R.string.cancel))
                    }
                    Button(
                        onClick = {
                            val token = authorizationToken ?: return@Button
                            submitted = true
                            onAuthorize(
                                plugin.packageName,
                                plugin.serviceName,
                                token,
                                reauthorize,
                            )
                        },
                        enabled = authorizationToken != null && !submitted,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag(
                                if (authorizationLoading) {
                                    "extension-authorization-confirm-loading"
                                } else {
                                    "extension-authorization-confirm"
                                }
                            )
                            .semantics {
                                if (authorizationLoading) {
                                    contentDescription = confirmLabel
                                    stateDescription = loadingDescription
                                    liveRegion = LiveRegionMode.Polite
                                }
                            },
                    ) {
                        if (authorizationLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clearAndSetSemantics { },
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(confirmLabel)
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .testTag("extension-authorization-bottom-safe-space"),
                )
            }
        }
    }
}

@Composable
private fun ExtensionApplicationIcon(
    plugin: InstalledPlugin,
    size: Dp,
    fallbackIconSize: Dp,
    warningBadgeSize: Dp,
) {
    val context = LocalContext.current
    val applicationIcon = remember(
        context,
        plugin.packageName,
        plugin.installed,
    ) {
        if (plugin.installed) {
            runCatching {
                context.packageManager.getApplicationIcon(plugin.packageName)
            }.getOrNull()
        } else {
            null
        }
    }
    val fallbackContainerColor = when (plugin.state) {
        ExtensionState.ENABLED -> MaterialTheme.colorScheme.secondaryContainer
        ExtensionState.DISABLED -> MaterialTheme.colorScheme.surfaceVariant
        ExtensionState.INCOMPATIBLE,
        ExtensionState.UNHEALTHY -> MaterialTheme.colorScheme.errorContainer
    }
    val fallbackContentColor = when (plugin.state) {
        ExtensionState.ENABLED -> MaterialTheme.colorScheme.onSecondaryContainer
        ExtensionState.DISABLED -> MaterialTheme.colorScheme.onSurfaceVariant
        ExtensionState.INCOMPATIBLE,
        ExtensionState.UNHEALTHY -> MaterialTheme.colorScheme.onErrorContainer
    }

    Box(modifier = Modifier.size(size)) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = MaterialTheme.shapes.extraLarge,
            color = fallbackContainerColor,
            contentColor = fallbackContentColor,
        ) {
            if (applicationIcon == null) {
                ExtensionFallbackIcon(iconSize = fallbackIconSize)
            } else {
                SubcomposeAsyncImage(
                    model = applicationIcon,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    loading = {
                        ExtensionFallbackIcon(iconSize = fallbackIconSize)
                    },
                    error = {
                        ExtensionFallbackIcon(iconSize = fallbackIconSize)
                    },
                )
            }
        }
        if (plugin.hasVisibleWarning) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(warningBadgeSize),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shadowElevation = 2.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(warningBadgeSize * 0.6f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ExtensionFallbackIcon(
    iconSize: Dp,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Extension,
            contentDescription = null,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun ExtensionIdentityHeader(
    plugin: InstalledPlugin,
    bidiFormatter: UiBidiFormatter,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ExtensionApplicationIcon(
                plugin = plugin,
                size = 72.dp,
                fallbackIconSize = 36.dp,
                warningBadgeSize = 24.dp,
            )
            Text(
                text = plugin.displayName
                    ?.takeIf(String::isNotBlank)
                    ?.let(bidiFormatter::natural)
                    ?: bidiFormatter.ltr(plugin.packageName),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            plugin.developer?.takeIf(String::isNotBlank)?.let { developer ->
                Text(
                    text = bidiFormatter.natural(developer),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(
                    space = 8.dp,
                    alignment = Alignment.CenterHorizontally,
                ),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ExtensionStatePill(plugin)
                plugin.version?.takeIf(String::isNotBlank)?.let { version ->
                    ExtensionMetadataPill(
                        text = bidiFormatter.ltr("v$version"),
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExtensionStatePill(plugin: InstalledPlugin) {
    val containerColor = when (plugin.state) {
        ExtensionState.ENABLED -> MaterialTheme.colorScheme.secondaryContainer
        ExtensionState.DISABLED -> MaterialTheme.colorScheme.surfaceVariant
        ExtensionState.INCOMPATIBLE,
        ExtensionState.UNHEALTHY -> MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = when (plugin.state) {
        ExtensionState.ENABLED -> MaterialTheme.colorScheme.onSecondaryContainer
        ExtensionState.DISABLED -> MaterialTheme.colorScheme.onSurfaceVariant
        ExtensionState.INCOMPATIBLE,
        ExtensionState.UNHEALTHY -> MaterialTheme.colorScheme.onErrorContainer
    }
    ExtensionMetadataPill(
        text = extensionStateLabel(plugin.state),
        containerColor = containerColor,
        contentColor = contentColor,
    )
}

@Composable
private fun ExtensionMetadataPill(
    text: String,
    containerColor: Color,
    contentColor: Color,
) {
    Surface(
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .widthIn(max = 160.dp)
                .padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun ExtensionAuthorizationIdentity(
    plugin: InstalledPlugin,
    bidiFormatter: UiBidiFormatter,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top,
            ) {
                ExtensionApplicationIcon(
                    plugin = plugin,
                    size = 56.dp,
                    fallbackIconSize = 28.dp,
                    warningBadgeSize = 20.dp,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = plugin.displayName
                            ?.takeIf(String::isNotBlank)
                            ?.let(bidiFormatter::natural)
                            ?: bidiFormatter.ltr(plugin.packageName),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() },
                    )
                    plugin.developer?.takeIf(String::isNotBlank)?.let { developer ->
                        Text(
                            text = bidiFormatter.natural(developer),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        ExtensionStatePill(plugin)
                        plugin.version?.takeIf(String::isNotBlank)?.let { version ->
                            ExtensionMetadataPill(
                                text = bidiFormatter.ltr("v$version"),
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExtensionAccessOverview(
    plugin: InstalledPlugin,
    capabilitiesExpanded: Boolean,
    onCapabilitiesExpandedChange: (Boolean) -> Unit,
    networkOriginsExpanded: Boolean,
    onNetworkOriginsExpandedChange: (Boolean) -> Unit,
    bidiFormatter: UiBidiFormatter,
) {
    val capabilityCount = plugin.capabilityPermissions.size
    val grantedCapabilityCount = plugin.capabilityPermissions.count { it.granted }
    val originCount = plugin.networkOrigins.size
    val approvedOriginCount = plugin.networkOrigins.count {
        it in plugin.approvedNetworkOrigins
    }
    val locale = LocalConfiguration.current.locales[0]
    val numberFormat = remember(locale) {
        NumberFormat.getIntegerInstance(locale)
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column {
            if (capabilityCount > 0) {
                ExtensionDisclosureHeader(
                    title = stringResource(string.feat_setting_extension_capabilities),
                    summary = bidiFormatter.natural(
                        stringResource(
                            string.feat_setting_extension_capability_summary,
                            numberFormat.format(grantedCapabilityCount),
                            numberFormat.format(capabilityCount),
                        )
                    ),
                    icon = Icons.Rounded.Security,
                    expanded = capabilitiesExpanded,
                    onExpandedChange = onCapabilitiesExpandedChange,
                    testTag = "extension-capabilities-disclosure",
                )
                AnimatedVisibility(visible = capabilitiesExpanded) {
                    ExtensionCapabilityList(
                        plugin = plugin,
                        bidiFormatter = bidiFormatter,
                    )
                }
            }
            if (capabilityCount > 0 && originCount > 0) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
            if (originCount > 0) {
                ExtensionDisclosureHeader(
                    title = stringResource(string.feat_setting_extension_network_access),
                    summary = bidiFormatter.natural(
                        stringResource(
                            string.feat_setting_extension_network_origin_summary,
                            numberFormat.format(approvedOriginCount),
                            numberFormat.format(originCount),
                        )
                    ),
                    icon = Icons.Rounded.Public,
                    expanded = networkOriginsExpanded,
                    onExpandedChange = onNetworkOriginsExpandedChange,
                    testTag = "extension-network-origins-disclosure",
                )
                AnimatedVisibility(visible = networkOriginsExpanded) {
                    ExtensionNetworkOriginList(
                        plugin = plugin,
                        bidiFormatter = bidiFormatter,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExtensionDisclosureHeader(
    title: String,
    summary: String,
    icon: ImageVector,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    testTag: String,
    titleIsHeading: Boolean = false,
) {
    val expansionState = stringResource(
        if (expanded) {
            string.ui_state_expanded
        } else {
            string.ui_state_collapsed
        }
    )
    ListItem(
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = if (titleIsHeading) {
                    Modifier.semantics { heading() }
                } else {
                    Modifier
                },
            )
        },
        supportingContent = {
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingContent = {
            ExtensionLeadingIcon(icon = icon)
        },
        trailingContent = {
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (expanded) {
                        Icons.Rounded.ExpandLess
                    } else {
                        Icons.Rounded.ExpandMore
                    },
                    contentDescription = null,
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .clickable(
                role = Role.Button,
                onClick = { onExpandedChange(!expanded) },
            )
            .semantics(mergeDescendants = true) {
                stateDescription = expansionState
            }
            .testTag(testTag),
    )
}

@Composable
private fun ExtensionLeadingIcon(
    icon: ImageVector,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = contentColor,
    ) {
        Box(
            modifier = Modifier.size(44.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun ExtensionTechnicalIdentity(
    plugin: InstalledPlugin,
    bidiFormatter: UiBidiFormatter,
) {
    val values = listOf(
        stringResource(string.feat_setting_extension_package) to
            bidiFormatter.ltr(plugin.packageName),
        stringResource(string.feat_setting_extension_service) to
            bidiFormatter.ltr(plugin.serviceName),
        stringResource(string.feat_setting_extension_certificate_sha256) to
            bidiFormatter.ltr(plugin.certificateSha256.chunked(16).joinToString(" ")),
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        values.forEachIndexed { index, (label, value) ->
            ListItem(
                headlineContent = {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                supportingContent = {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                    )
                },
                colors = ListItemDefaults.colors(
                    containerColor = Color.Transparent
                ),
                modifier = Modifier.semantics(mergeDescendants = true) {},
            )
            if (index != values.lastIndex) {
                HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
            }
        }
    }
}

@Composable
private fun ExtensionCapabilityList(
    plugin: InstalledPlugin,
    bidiFormatter: UiBidiFormatter,
    reviewingAuthorization: Boolean = false,
    showTopDivider: Boolean = true,
) {
    if (plugin.capabilityPermissions.isEmpty()) {
        Text("—", style = MaterialTheme.typography.bodyMedium)
        return
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        if (showTopDivider) {
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }
        plugin.capabilityPermissions.forEachIndexed { index, permission ->
            val nameResource = extensionCapabilityNameResource(permission.id)
            val capabilityName = nameResource
                ?.let { resource -> stringResource(resource) }
                ?: bidiFormatter.ltr(permission.id)
            val reason = permission.reason
                .takeIf(String::isNotBlank)
                ?.let(bidiFormatter::natural)
            val requirement = stringResource(
                if (permission.required) {
                    string.feat_setting_extension_capability_required
                } else {
                    string.feat_setting_extension_capability_optional
                }
            )
            val grantState = if (reviewingAuthorization) {
                null
            } else {
                stringResource(
                    if (permission.granted) {
                        string.feat_setting_extension_capability_granted
                    } else {
                        string.feat_setting_extension_capability_not_granted
                    }
                )
            }
            val permissionNeedsAttention = permission.required && !permission.granted
            ListItem(
                headlineContent = {
                    Text(
                        text = capabilityName,
                        style = MaterialTheme.typography.titleSmall,
                    )
                },
                supportingContent = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        reason?.let { explanation ->
                            Text(
                                text = explanation,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = requirement,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                trailingContent = {
                    Icon(
                        imageVector = if (!reviewingAuthorization && permission.granted) {
                            Icons.Rounded.CheckCircle
                        } else {
                            Icons.Rounded.Security
                        },
                        contentDescription = null,
                        tint = if (reviewingAuthorization) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else if (permissionNeedsAttention) {
                            MaterialTheme.colorScheme.error
                        } else if (permission.granted) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                },
                colors = ListItemDefaults.colors(
                    containerColor = Color.Transparent
                ),
                modifier = Modifier.semantics(mergeDescendants = true) {
                    grantState?.let { state ->
                        stateDescription = state
                    }
                },
            )
            if (index != plugin.capabilityPermissions.lastIndex) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

@Composable
private fun ExtensionAuthorizationNetworkOrigins(
    plugin: InstalledPlugin,
    bidiFormatter: UiBidiFormatter,
) {
    val origins = plugin.networkOrigins.sorted()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column {
            origins.forEachIndexed { index, origin ->
                ListItem(
                    headlineContent = {
                        Text(
                            text = bidiFormatter.ltr(origin),
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                        )
                    },
                    leadingContent = {
                        ExtensionLeadingIcon(icon = Icons.Rounded.Public)
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = Color.Transparent
                    ),
                    modifier = Modifier.semantics(mergeDescendants = true) {},
                )
                if (
                    index != origins.lastIndex ||
                    plugin.networkOriginSettingFields.isNotEmpty()
                ) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
            if (plugin.networkOriginSettingFields.isNotEmpty()) {
                Text(
                    text = stringResource(
                        string.feat_setting_extension_network_origin_settings,
                        plugin.networkOriginSettingFields
                            .sorted()
                            .joinToString(transform = bidiFormatter::ltr),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun ExtensionNetworkOriginList(
    plugin: InstalledPlugin,
    bidiFormatter: UiBidiFormatter,
) {
    val origins = plugin.networkOrigins.sorted()
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        origins.forEachIndexed { index, origin ->
            val approved = origin in plugin.approvedNetworkOrigins
            val approvalState = stringResource(
                if (approved) {
                    string.feat_setting_extension_capability_granted
                } else {
                    string.feat_setting_extension_capability_not_granted
                }
            )
            ListItem(
                headlineContent = {
                    Text(
                        text = bidiFormatter.ltr(origin),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                    )
                },
                supportingContent = {
                    Text(
                        text = approvalState,
                        color = if (approved) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                },
                trailingContent = {
                    Icon(
                        imageVector = Icons.Rounded.Public,
                        contentDescription = null,
                        tint = if (approved) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                },
                colors = ListItemDefaults.colors(
                    containerColor = Color.Transparent
                ),
                modifier = Modifier.semantics(mergeDescendants = true) {
                    stateDescription = approvalState
                },
            )
            if (index != origins.lastIndex) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

@Composable
private fun ExtensionWarningSummary(
    plugin: InstalledPlugin,
    unapprovedNetworkOrigins: Set<String>,
    bidiFormatter: UiBidiFormatter,
) {
    ExtensionWarningMessages(
        extensionWarningMessages(
            plugin = plugin,
            unapprovedNetworkOrigins = unapprovedNetworkOrigins,
            bidiFormatter = bidiFormatter,
        )
    )
}

@Composable
private fun extensionWarningMessages(
    plugin: InstalledPlugin,
    unapprovedNetworkOrigins: Set<String>,
    bidiFormatter: UiBidiFormatter,
): List<String> = buildList {
    if (plugin.trusted && unapprovedNetworkOrigins.isNotEmpty()) {
        add(
            stringResource(
            string.feat_setting_extension_network_reauthorization_required,
            unapprovedNetworkOrigins
                .sorted()
                .joinToString(transform = bidiFormatter::ltr),
            )
        )
    }
    if (plugin.inspectionError != null) {
        add(stringResource(string.feat_setting_extension_inspection_failed))
    }
    if (!plugin.installed) {
        add(stringResource(string.feat_setting_extension_not_installed))
    }
    if (plugin.signatureChanged) {
        add(stringResource(string.feat_setting_extension_signature_changed))
    }
    if (
        plugin.state == ExtensionState.INCOMPATIBLE ||
        plugin.state == ExtensionState.UNHEALTHY
    ) {
        add(extensionStateLabel(plugin.state))
    }
}

@Composable
private fun ExtensionWarningMessages(
    messages: List<String>,
    modifier: Modifier = Modifier,
) {
    val description = messages.joinToString(separator = "\n")
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                error(description)
                liveRegion = LiveRegionMode.Polite
            },
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = null,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                messages.forEach { message ->
                    Text(text = message)
                }
            }
        }
    }
}

@Composable
private fun ExtensionWarning(
    message: String,
    modifier: Modifier = Modifier,
) {
    ExtensionWarningMessages(
        messages = listOf(message),
        modifier = modifier,
    )
}

@Composable
private fun ExtensionSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        content()
    }
}

@Composable
private fun ExtensionDataRemovalDialog(
    plugin: InstalledPlugin,
    title: String,
    body: String,
    confirmLabel: String,
    dialogTag: String,
    confirmTag: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(dialogTag),
        title = { Text(title) },
        text = {
            ExtensionDataRemovalBody(
                plugin = plugin,
                body = body,
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag(confirmTag),
            ) {
                Text(
                    text = confirmLabel,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun ExtensionDataRemovalBody(
    plugin: InstalledPlugin,
    body: String,
) {
    val bidiFormatter = rememberUiBidiFormatter()
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .testTag("extension-data-removal-target"),
    ) {
        plugin.displayName?.takeIf(String::isNotBlank)?.let { displayName ->
            Text(
                text = bidiFormatter.natural(displayName),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Text(
            text = bidiFormatter.ltr(plugin.packageName),
            style = if (plugin.displayName.isNullOrBlank()) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.bodySmall
            },
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.testTag("extension-data-removal-package"),
        )
        Text(body)
    }
}

@Composable
private fun ExtensionUnavailableScreen(
    modifier: Modifier,
    contentPadding: PaddingValues,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp)
            .testTag("extension-plugin-unavailable"),
        contentAlignment = Alignment.TopStart,
    ) {
        Text(
            text = stringResource(string.feat_setting_extension_not_installed),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun ExtensionEmptyState(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.Extension,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ExtensionPageContent(
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = ExtensionPageMaxWidth)
                .fillMaxWidth(),
            content = content,
        )
    }
}

private val InstalledPlugin.stableKey: String
    get() = "$packageName/$serviceName"

private val InstalledPlugin.hasVisibleWarning: Boolean
    get() = signatureChanged ||
        !installed ||
        inspectionError != null ||
        state == ExtensionState.INCOMPATIBLE ||
        state == ExtensionState.UNHEALTHY ||
        (trusted && (networkOrigins - approvedNetworkOrigins).isNotEmpty())

private val ExtensionPluginActionAvailability.hasControlActions: Boolean
    get() = enable || settings || reauthorize || disable

private fun ExtensionPluginOperationState.targets(plugin: InstalledPlugin): Boolean {
    val operation = (this as? ExtensionPluginOperationState.Running)?.operation ?: return false
    return when (operation) {
        ExtensionPluginOperation.Refresh -> false
        is ExtensionPluginOperation.Enable ->
            operation.packageName == plugin.packageName &&
                operation.serviceName == plugin.serviceName
        is ExtensionPluginOperation.Reauthorize ->
            operation.packageName == plugin.packageName &&
                operation.serviceName == plugin.serviceName
        is ExtensionPluginOperation.Disable -> operation.extensionId == plugin.extensionId
        is ExtensionPluginOperation.ClearData ->
            operation.packageName == plugin.packageName &&
                operation.serviceName == plugin.serviceName
        is ExtensionPluginOperation.Revoke ->
            operation.packageName == plugin.packageName &&
                operation.serviceName == plugin.serviceName
    }
}

private fun InstalledPlugin.actionTestTag(action: String): String =
    "extension-plugin-action-$action:$stableKey"

private fun String.shortCertificateFingerprint(): String {
    val prefix = take(16).chunked(8).joinToString(" ")
    return if (length > 16) "$prefix…" else prefix
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
