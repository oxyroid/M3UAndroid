package com.m3u.features.foryou.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m3u.material.components.Icon
import com.m3u.material.components.OuterRow
import com.m3u.material.ktx.isTelevision
import com.m3u.material.model.LocalSpacing
import com.m3u.material.shape.AbsoluteSmoothCornerShape
import com.m3u.ui.FontFamilies
import androidx.tv.material3.ListItem as TvListItem
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Text as TvText

@Composable
internal fun PlaylistItem(
    label: String,
    type: String?,
    typeWithSource: String?,
    count: Int,
    local: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tv = isTelevision()
    if (!tv) {
        SmartphonePlaylistItemImpl(
            label = label,
            type = type,
            typeWithSource = typeWithSource,
            count = count,
            local = local,
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier
        )
    } else {
        TvPlaylistItemImpl(
            label = label,
            type = type,
            typeWithSource = typeWithSource,
            count = count,
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
    typeWithSource: String?,
    count: Int,
    local: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val theme = MaterialTheme.colorScheme
    val currentContentColor by animateColorAsState(
        targetValue = theme.onSurface,
        label = "playlist-item-content"
    )
    OutlinedCard(
        shape = AbsoluteSmoothCornerShape(spacing.medium, 65),
        border = CardDefaults.outlinedCardBorder(local),
        modifier = modifier.semantics(mergeDescendants = true) { }
    ) {
        OuterRow(
            modifier = Modifier
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                ),
            verticalAlignment = Alignment.Top
        ) {
            if (local) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.DriveFileMove,
                    contentDescription = null,
                    tint = currentContentColor
                )
            }
            Column(
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.alignByBaseline()
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        baselineShift = BaselineShift.None
                    ),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (typeWithSource != null) {
                    Text(
                        text = typeWithSource.uppercase(),
                        style = MaterialTheme.typography.bodySmall.copy(
                            letterSpacing = 1.sp,
                            baselineShift = BaselineShift.Subscript,
                            fontFamily = FontFamilies.LexendExa,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.sp
                        ),
                        color = LocalContentColor.current.copy(0.45f)
                    )
                }
            }

            val currentPrimaryColor by animateColorAsState(
                targetValue = theme.primary,
                label = "playlist-item-primary"
            )
            val currentOnPrimaryColor by animateColorAsState(
                targetValue = theme.onPrimary,
                label = "playlist-item-on-primary"
            )
            Spacer(Modifier.weight(1f))
            Column(
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                Box(
                    modifier = Modifier
                        .clip(AbsoluteSmoothCornerShape(spacing.small, 65))
                        .background(currentPrimaryColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        color = currentOnPrimaryColor,
                        text = count.toString(),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            lineHeightStyle = LineHeightStyle(
                                alignment = LineHeightStyle.Alignment.Center,
                                trim = LineHeightStyle.Trim.None
                            )
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(
                            start = spacing.small,
                            end = spacing.small,
                            bottom = 2.dp,
                        ),
                        softWrap = false,
                        textAlign = TextAlign.Center,
                        fontFamily = FontFamilies.LexendExa
                    )
                }
            }
        }
    }
}

@Composable
private fun TvPlaylistItemImpl(
    label: String,
    type: String?,
    typeWithSource: String?,
    count: Int,
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
        supportingContent = if (typeWithSource != null) {
            {
                TvText(
                    text = typeWithSource,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TvMaterialTheme.typography.bodyMedium
                )
            }
        } else null,
        trailingContent = {
            Box(
                modifier = Modifier
                    .clip(AbsoluteSmoothCornerShape(spacing.small, 65))
                    .background(theme.primary),
                contentAlignment = Alignment.Center
            ) {
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
                        start = spacing.small,
                        end = spacing.small,
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
