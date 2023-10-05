package com.m3u.features.about.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Card
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.m3u.features.about.model.Contributor
import com.m3u.ui.model.LocalSpacing
import com.m3u.ui.model.LocalTheme

@Composable
@OptIn(ExperimentalMaterialApi::class)
internal fun ContributorItem(
    contributor: Contributor,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val theme = LocalTheme.current
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.small),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(spacing.small)
        ) {
            AsyncImage(
                model = contributor.avatar,
                contentDescription = contributor.name,
                modifier = Modifier
                    .clip(CircleShape)
                    .size(48.dp)
            )
            Column {
                Text(
                    text = contributor.name,
                    color = theme.primary,
                    style = MaterialTheme.typography.subtitle1
                )
                Text(
                    text = contributor.contributions.toString(),
                    style = MaterialTheme.typography.subtitle2
                )
            }
        }
    }
}