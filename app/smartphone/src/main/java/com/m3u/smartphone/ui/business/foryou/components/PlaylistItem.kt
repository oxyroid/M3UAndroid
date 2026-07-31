package com.m3u.smartphone.ui.business.foryou.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.m3u.core.foundation.components.CircularProgressIndicator
import com.m3u.core.foundation.components.AbsoluteSmoothCornerShape
import com.m3u.i18n.R.plurals
import com.m3u.smartphone.ui.material.model.LocalSpacing
import java.util.Locale

@Composable
internal fun PlaylistItem(
    label: String,
    type: String?,
    count: Int,
    subscribingOrRefreshing: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    Card(
        shape = AbsoluteSmoothCornerShape(spacing.medium, 65),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = modifier
            .semantics(mergeDescendants = true) { }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            },
            supportingContent = {
                if (type != null) {
                    Text(
                        text = type.uppercase(Locale.ROOT),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            leadingContent = {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.PlaylistPlay,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
            },
            trailingContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.widthIn(max = 120.dp),
                ) {
                    if (subscribingOrRefreshing) {
                        CircularProgressIndicator(
                            size = 16.dp,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(spacing.small))
                    }
                    Text(
                        text = pluralStringResource(
                            plurals.feat_foryou_channel_count,
                            count,
                            count,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                    )
                }
            },
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent,
            ),
            modifier = Modifier.heightIn(min = 88.dp),
        )
    }
}
