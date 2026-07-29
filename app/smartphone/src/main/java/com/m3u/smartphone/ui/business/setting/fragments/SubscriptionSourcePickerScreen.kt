package com.m3u.smartphone.ui.business.setting.fragments

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.m3u.business.setting.ProviderDiscoveryState
import com.m3u.business.setting.ProviderOperationState
import com.m3u.business.setting.ProviderSubscriptionSource
import com.m3u.business.setting.SettingProperties
import com.m3u.business.setting.subscriptionSources
import com.m3u.data.database.model.DataSource
import com.m3u.data.repository.provider.SubscriptionProviderExecutionKind
import com.m3u.i18n.R.string
import com.m3u.smartphone.ui.material.ktx.plus
import com.m3u.smartphone.ui.material.ktx.rememberUiBidiFormatter

private val SubscriptionPickerMaxWidth = 640.dp

private data class SubscriptionSourceRow(
    val key: String,
    val label: String,
    val supporting: String?,
    val supportingContentDescription: String? = null,
    val icon: ImageVector,
    val ordinarySource: DataSource? = null,
    val providerSource: ProviderSubscriptionSource? = null,
)

@Composable
context(properties: SettingProperties)
internal fun SubscriptionSourcePickerScreen(
    dataOperationInProgress: Boolean,
    subscriptionSubmissionBlocked: Boolean,
    providerDiscoveryState: ProviderDiscoveryState,
    providerOperationState: ProviderOperationState,
    onSelectSubscriptionProviderVariant: (String, String) -> Unit,
    onRetryProviderDiscovery: () -> Unit,
    onOpenEditor: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val bidiFormatter = rememberUiBidiFormatter()
    val ordinarySources = listOf(
        SubscriptionSourceRow(
            key = DataSource.M3U.subscriptionSelectionKey(),
            label = stringResource(DataSource.M3U.resId),
            supporting = stringResource(
                string.feat_setting_playlist_source_m3u_description
            ),
            icon = Icons.Rounded.Link,
            ordinarySource = DataSource.M3U,
        ),
        SubscriptionSourceRow(
            key = DataSource.Xtream.subscriptionSelectionKey(),
            label = stringResource(DataSource.Xtream.resId),
            supporting = stringResource(
                string.feat_setting_playlist_source_xtream_description
            ),
            icon = Icons.Rounded.Cloud,
            ordinarySource = DataSource.Xtream,
        ),
    )
    val providerSources = providerDiscoveryState.subscriptionSources()
        .map { source ->
            val stableProviderId = bidiFormatter.ltr(source.providerId.value)
            val variantName = bidiFormatter.natural(source.displayName)
                .ifBlank { bidiFormatter.natural(source.providerDisplayName) }
                .ifBlank { stableProviderId }
            val providerName = bidiFormatter.natural(source.providerDisplayName)
                .ifBlank { stableProviderId }
            val discloseStableIdentity =
                source.executionKind == SubscriptionProviderExecutionKind.EXTERNAL
            val supporting = if (discloseStableIdentity) {
                stringResource(
                    string.feat_setting_provider_choice_with_identifier,
                    providerName,
                    stableProviderId,
                )
            } else {
                providerName.takeUnless { name -> name == variantName }
            }
            SubscriptionSourceRow(
                key = source.subscriptionSelectionKey(),
                label = variantName,
                supporting = supporting,
                supportingContentDescription = if (discloseStableIdentity) {
                    stringResource(
                        string.feat_setting_provider_choice_with_identifier_description,
                        providerName,
                        stableProviderId,
                    )
                } else {
                    null
                },
                icon = Icons.Rounded.Extension,
                providerSource = source,
            )
        }
    val enabled = !providerOperationState.isBusy &&
        !dataOperationInProgress &&
        !subscriptionSubmissionBlocked
    val providerNotice =
        if (
            providerDiscoveryState is ProviderDiscoveryState.Ready &&
            providerSources.isEmpty()
        ) {
            ProviderDiscoveryNotice.EMPTY
        } else {
            providerDiscoveryNotice(
                state = providerDiscoveryState,
                providerSelected = false,
            )
        }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("playlist-source-picker"),
        contentPadding = contentPadding + PaddingValues(vertical = 16.dp),
    ) {
        item(key = "description") {
            SubscriptionPickerPageContent {
                Text(
                    text = stringResource(
                        string.feat_setting_playlist_source_picker_description
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        item(key = "built-in-heading") {
            SubscriptionPickerPageContent {
                SubscriptionPickerHeading(
                    text = stringResource(
                        string.feat_setting_playlist_built_in_sources
                    )
                )
            }
        }
        item(key = "built-in-sources") {
            SubscriptionPickerPageContent {
                SubscriptionSourceGroup(
                    sources = ordinarySources,
                    enabled = enabled,
                    onClick = { source ->
                        properties.selectedState.value =
                            requireNotNull(source.ordinarySource)
                        onOpenEditor(source.key)
                    },
                )
            }
        }
        item(key = "providers-heading") {
            SubscriptionPickerPageContent {
                SubscriptionPickerHeading(
                    text = stringResource(string.feat_setting_playlist_providers)
                )
            }
        }
        if (providerSources.isNotEmpty()) {
            item(key = "provider-sources") {
                SubscriptionPickerPageContent {
                    SubscriptionSourceGroup(
                        sources = providerSources,
                        enabled = enabled,
                        onClick = { source ->
                            val provider = requireNotNull(source.providerSource)
                            properties.selectedState.value = DataSource.Provider
                            onSelectSubscriptionProviderVariant(
                                provider.providerId.value,
                                provider.providerKind.value,
                            )
                            onOpenEditor(source.key)
                        },
                    )
                }
            }
        }
        when (providerNotice) {
            ProviderDiscoveryNotice.NONE -> Unit
            ProviderDiscoveryNotice.LOADING -> {
                item(key = "providers-loading") {
                    SubscriptionPickerPageContent {
                        ProviderDiscoveryLoadingNotice(
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
            ProviderDiscoveryNotice.EMPTY -> {
                item(key = "providers-empty") {
                    SubscriptionPickerPageContent {
                        ProviderDiscoveryRetryNotice(
                            message = stringResource(
                                string.feat_setting_provider_discovery_empty
                            ),
                            onRetry = onRetryProviderDiscovery,
                            enabled = enabled,
                            testTag = "provider-discovery-empty",
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
            ProviderDiscoveryNotice.FAILED -> {
                item(key = "providers-failed") {
                    SubscriptionPickerPageContent {
                        ProviderDiscoveryRetryNotice(
                            message = stringResource(
                                string.feat_setting_provider_discovery_failed
                            ),
                            onRetry = onRetryProviderDiscovery,
                            enabled = enabled,
                            testTag = "provider-discovery-failed",
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SubscriptionSourceGroup(
    sources: List<SubscriptionSourceRow>,
    enabled: Boolean,
    onClick: (SubscriptionSourceRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column {
            sources.forEachIndexed { index, source ->
                SubscriptionSourceListItem(
                    source = source,
                    enabled = enabled,
                    onClick = { onClick(source) },
                )
                if (index != sources.lastIndex) {
                    SubscriptionPickerDivider()
                }
            }
        }
    }
}

@Composable
private fun SubscriptionSourceListItem(
    source: SubscriptionSourceRow,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(
                text = source.label,
                style = MaterialTheme.typography.titleMedium.copy(
                    textDirection = TextDirection.ContentOrLtr,
                ),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = source.supporting?.let { supporting ->
            {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        textDirection = TextDirection.ContentOrLtr,
                    ),
                    modifier = source.supportingContentDescription?.let { description ->
                        Modifier.clearAndSetSemantics {
                            contentDescription = description
                        }
                    } ?: Modifier,
                )
            }
        },
        leadingContent = {
            Icon(imageVector = source.icon, contentDescription = null)
        },
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .alpha(if (enabled) 1f else 0.38f)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .testTag("playlist-source:${source.key}"),
    )
}

@Composable
private fun SubscriptionPickerHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp)
            .semantics { heading() },
    )
}

@Composable
private fun SubscriptionPickerDivider() {
    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
}

@Composable
private fun SubscriptionPickerPageContent(
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
                .widthIn(max = SubscriptionPickerMaxWidth)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.Top,
        ) {
            content()
        }
    }
}
