package com.m3u.features.setting.components

import androidx.compose.foundation.clickable
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.m3u.data.database.entity.Stream

@Composable
internal fun MutedStreamItem(
    stream: Stream,
    onBanned: () -> Unit,
    modifier: Modifier = Modifier
) {
    ListItem(
        headlineContent = {
            val text = stream.title
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            val text = stream.url
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
                onClick = onBanned
            )
            .then(modifier)
    )
}