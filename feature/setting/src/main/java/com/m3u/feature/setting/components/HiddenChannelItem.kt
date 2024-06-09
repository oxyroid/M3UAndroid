package com.m3u.feature.setting.components

import androidx.compose.foundation.clickable
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.m3u.data.database.model.Channel

@Composable
internal fun HiddenChannelItem(
    channel: Channel,
    onHidden: () -> Unit,
    modifier: Modifier = Modifier
) {
    ListItem(
        headlineContent = {
            val text = channel.title
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold
            )
        },
        supportingContent = {
            val text = channel.url
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        modifier = Modifier
            .clickable(
                enabled = true,
                onClickLabel = null,
                role = Role.Button,
                onClick = onHidden
            )
            .then(modifier)
    )
}