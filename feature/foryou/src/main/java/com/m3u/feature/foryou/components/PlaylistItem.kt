package com.m3u.feature.foryou.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m3u.material.components.CircularProgressIndicator
import com.m3u.material.components.Icon
import com.m3u.material.ktx.tv
import com.m3u.material.model.LocalSpacing
import com.m3u.material.shape.AbsoluteSmoothCornerShape
import com.m3u.ui.Badge
import com.m3u.ui.FontFamilies
import androidx.tv.material3.ListItem as TvListItem
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Text as TvText

@Composable
internal fun PlaylistItem(
    label: String,
    type: String?,
    count: Int,
    local: Boolean,
    subscribingOrRefreshing: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tv = tv()
    if (!tv) {
        SmartphonePlaylistItemImpl(
            label = label,
            type = type,
            count = count,
            local = local,
            subscribing = subscribingOrRefreshing,
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier
        )
    } else {
        TvPlaylistItemImpl(
            label = label,
            type = type,
            count = count,
            subscribing = subscribingOrRefreshing,
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier
        )
    }
}

@Composable
private fun SmartphonePlaylistItemImpl(
    label: String,
    type: String?,
    count: Int,
    local: Boolean,
    subscribing: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    OutlinedCard(
        shape = AbsoluteSmoothCornerShape(spacing.medium, 65),
        border = CardDefaults.outlinedCardBorder(local),
        colors = CardDefaults.cardColors(Color.Transparent),
        modifier = modifier.semantics(mergeDescendants = true) { }
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        baselineShift = BaselineShift.None
                    ),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            supportingContent = {
                Text(
                    text = type?.uppercase().orEmpty(),
                    style = MaterialTheme.typography.bodySmall.copy(
                        letterSpacing = 1.sp,
                        baselineShift = BaselineShift.Subscript,
                        fontFamily = FontFamilies.LexendExa,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp
                    ),
                    color = LocalContentColor.current.copy(0.45f)
                )
            },
            trailingContent = {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(spacing.extraSmall)
                ) {
                    Badge {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall)
                        ) {
                            if (subscribing) {
                                CircularProgressIndicator(
                                    color = LocalContentColor.current,
                                    size = 8.dp
                                )
                            }
                            Text(
                                text = count.toString(),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    lineHeightStyle = LineHeightStyle(
                                        alignment = LineHeightStyle.Alignment.Center,
                                        trim = LineHeightStyle.Trim.None
                                    )
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                softWrap = false,
                                textAlign = TextAlign.Center,
                                fontFamily = FontFamilies.LexendExa
                            )
                        }
                    }
                    Row(
                        Modifier.height(16.dp)
                    ) {
                        if (local) {
                            Badge(
                                color = MaterialTheme.colorScheme.secondary,
                                shape = AbsoluteSmoothCornerShape(
                                    cornerRadiusTL = spacing.extraSmall,
                                    cornerRadiusTR = spacing.extraSmall,
                                    cornerRadiusBL = spacing.extraSmall,
                                    cornerRadiusBR = spacing.small
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.DriveFileMove,
                                    contentDescription = null,
                                )
                            }
                        }
                    }
                }
            },
            colors = ListItemDefaults.colors(Color.Transparent),
            modifier = Modifier.combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
        )
    }
}

@Composable
private fun TvPlaylistItemImpl(
    label: String,
    type: String?,
    count: Int,
    subscribing: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val theme = TvMaterialTheme.colorScheme
    TvListItem(
        selected = false,
        onClick = onClick,
        onLongClick = onLongClick,
        headlineContent = {
            TvText(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            TvText(
                text = type?.uppercase().orEmpty(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TvMaterialTheme.typography.bodyMedium,
                fontFamily = FontFamilies.LexendExa
            )
        },
        trailingContent = {
            Row(
                modifier = Modifier
                    .clip(AbsoluteSmoothCornerShape(spacing.small, 65))
                    .background(theme.primary)
                    .padding(horizontal = spacing.small),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall)
            ) {
                if (subscribing) {
                    CircularProgressIndicator(
                        color = theme.onPrimary
                    )
                }
                TvText(
                    color = theme.onPrimary,
                    text = count.toString(),
                    style = TvMaterialTheme.typography.bodyMedium.copy(
                        lineHeightStyle = LineHeightStyle(
                            alignment = LineHeightStyle.Alignment.Center,
                            trim = LineHeightStyle.Trim.None
                        )
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(
                        bottom = 2.dp,
                    ),
                    softWrap = false,
                    textAlign = TextAlign.Center,
                    fontFamily = FontFamilies.LexendExa
                )
            }
        },
        modifier = modifier
    )
}
