package com.m3u.feature.playlist.configuration.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.m3u.core.wrapper.Resource
import com.m3u.data.parser.xtream.XtreamInfo
import com.m3u.material.model.LocalSpacing
import com.m3u.ui.Badge
import com.m3u.ui.FontFamilies
import com.m3u.ui.TextBadge
import kotlinx.datetime.Instant

@Composable
internal fun XtreamPanel(
    info: Resource<XtreamInfo.UserInfo>,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val containerColor by animateColorAsState(
        targetValue = when (info) {
            Resource.Loading -> MaterialTheme.colorScheme.primaryContainer
            is Resource.Success -> MaterialTheme.colorScheme.surfaceContainer
            is Resource.Failure -> MaterialTheme.colorScheme.errorContainer
        },
        label = "xtream-panel-container-color"
    )
    val contentColor by animateColorAsState(
        targetValue = when (info) {
            Resource.Loading -> MaterialTheme.colorScheme.onPrimaryContainer
            is Resource.Success -> MaterialTheme.colorScheme.onSurface
            is Resource.Failure -> MaterialTheme.colorScheme.onErrorContainer
        },
        label = "xtream-panel-content-color"
    )
    ElevatedCard(
        modifier = modifier,
        colors = CardDefaults.elevatedCardColors(containerColor, contentColor)
    ) {
        Box(Modifier.padding(spacing.medium)) {
            when (info) {
                Resource.Loading -> {
                    CircularProgressIndicator()
                }

                is Resource.Success -> {
                    val userInfo = info.data
                    Column(
                        verticalArrangement = Arrangement.spacedBy(spacing.extraSmall)
                    ) {
                        Text(
                            text = userInfo.username.orEmpty(),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = Instant.fromEpochSeconds(
                                (userInfo.createdAt ?: "0").toLong()
                            ).toString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalContentColor.current.copy(0.54f)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(spacing.small),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextBadge(
                                text = userInfo.status.orEmpty()
                            )
                            if (userInfo.isTrial == "1") {
                                TextBadge(
                                    text = "Trial",
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                            Badge(
                                color = MaterialTheme.colorScheme.secondary
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Link,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "${userInfo.activeCons.orEmpty()}/${userInfo.maxConnections.orEmpty()}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontFamily = FontFamilies.LexendExa
                                    )
                                }
                            }
                        }
                    }
                }

                is Resource.Failure -> {
                    Text(
                        text = info.message.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}