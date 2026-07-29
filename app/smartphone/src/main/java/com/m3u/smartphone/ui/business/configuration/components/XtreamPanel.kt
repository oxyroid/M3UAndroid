package com.m3u.smartphone.ui.business.configuration.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.m3u.core.foundation.wrapper.Resource
import com.m3u.data.database.model.DataSource
import com.m3u.data.parser.xtream.XtreamUserInfo
import com.m3u.i18n.R.string
import com.m3u.smartphone.ui.material.ktx.safeDisplayText
import kotlin.time.Instant

@Composable
internal fun XtreamPanel(
    info: Resource<XtreamUserInfo>,
    modifier: Modifier = Modifier
) {
    val loadingDescription = stringResource(string.ui_state_loading)

    Surface(
        modifier = modifier.testTag("playlist-configuration-xtream"),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        when (info) {
            Resource.Loading -> {
                ListItem(
                    headlineContent = {
                        Text(
                            text = loadingDescription,
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Rounded.Cloud,
                            contentDescription = null,
                        )
                    },
                    trailingContent = {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .clearAndSetSemantics { },
                            strokeWidth = 2.dp,
                        )
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = Color.Transparent,
                        leadingIconColor =
                            MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .semantics {
                            stateDescription = loadingDescription
                            liveRegion = LiveRegionMode.Polite
                        },
                )
            }

            is Resource.Success -> {
                XtreamAccountContent(userInfo = info.data)
            }

            is Resource.Failure -> {
                ListItem(
                    headlineContent = {
                        Text(
                            text = stringResource(DataSource.Xtream.resId),
                        )
                    },
                    supportingContent = {
                        Text(
                            text = stringResource(
                                string.feat_setting_provider_subscription_failed
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Rounded.Warning,
                            contentDescription = null,
                        )
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = Color.Transparent,
                        leadingIconColor = MaterialTheme.colorScheme.error,
                        supportingColor =
                            MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

@Composable
private fun XtreamAccountContent(
    userInfo: XtreamUserInfo,
) {
    val username = userInfo.username
        .orEmpty()
        .safeDisplayText()
        .takeIf(String::isNotBlank)
        ?: stringResource(DataSource.Xtream.resId)
    val createdAt = userInfo.createdAt.toDisplayInstant()
    val status = userInfo.status
        .orEmpty()
        .safeDisplayText()
        .takeIf(String::isNotBlank)
    val activeConnections = userInfo.activeCons
        .orEmpty()
        .safeDisplayText()
    val maximumConnections = userInfo.maxConnections
        .orEmpty()
        .safeDisplayText()
    val connectionCount = if (
        activeConnections.isNotBlank() || maximumConnections.isNotBlank()
    ) {
        "$activeConnections/$maximumConnections"
    } else {
        null
    }

    Column {
        ListItem(
            headlineContent = {
                Text(
                    text = username,
                    style = MaterialTheme.typography.titleMedium.copy(
                        textDirection = TextDirection.ContentOrLtr,
                    ),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = createdAt?.let {
                {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            textDirection = TextDirection.Ltr,
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            leadingContent = {
                Icon(
                    imageVector = Icons.Rounded.Cloud,
                    contentDescription = null,
                )
            },
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent,
                supportingColor = MaterialTheme.colorScheme.onSurfaceVariant,
                leadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp),
        )

        if (
            status != null ||
            userInfo.isTrial == "1" ||
            connectionCount != null
        ) {
            FlowRow(
                modifier = Modifier.padding(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 16.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                status?.let {
                    XtreamInformationPill(
                        text = it,
                        textDirection = TextDirection.Ltr,
                    )
                }
                if (userInfo.isTrial == "1") {
                    XtreamInformationPill(
                        text = stringResource(
                            string.feat_playlist_configuration_xtream_trial
                        ),
                    )
                }
                connectionCount?.let {
                    XtreamInformationPill(
                        text = it,
                        icon = Icons.Rounded.Link,
                        textDirection = TextDirection.Ltr,
                    )
                }
            }
        }
    }
}

@Composable
private fun XtreamInformationPill(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    textDirection: TextDirection = TextDirection.Content,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 32.dp)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(
                    textDirection = textDirection,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun String?.toDisplayInstant(): String? {
    val epochSeconds = this?.toLongOrNull()?.takeIf { it >= 0L } ?: return null
    return runCatching {
        Instant.fromEpochSeconds(epochSeconds).toString()
    }.getOrNull()
}
