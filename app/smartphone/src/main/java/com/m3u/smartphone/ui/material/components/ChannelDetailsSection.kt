package com.m3u.smartphone.ui.material.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.ChannelDetails
import com.m3u.i18n.R.string
import com.m3u.smartphone.ui.material.model.LocalSpacing

/**
 * Synopsis, cast and rating for the channel a sheet was opened on.
 *
 * Shows nothing at all — not an error, not a placeholder — when the panel has
 * no description to give. Live channels never have one, and a sheet opened to
 * hide or favourite a channel should not be pushed around by an empty block.
 */
@Composable
fun ChannelDetailsSection(
    channel: Channel?,
    modifier: Modifier = Modifier,
) {
    val viewModel: ChannelDetailsViewModel = hiltViewModel()
    LaunchedEffect(channel?.id) { viewModel.load(channel) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val spacing = LocalSpacing.current

    when (val current = state) {
        ChannelDetailsState.Absent -> Unit

        ChannelDetailsState.Loading -> {
            if (channel == null) return
            Text(
                text = stringResource(string.ui_details_loading),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = modifier.padding(horizontal = spacing.medium),
            )
        }

        is ChannelDetailsState.Content -> Column(
            verticalArrangement = Arrangement.spacedBy(spacing.extraSmall),
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.medium),
        ) {
            current.details.Headline()
            current.details.cast?.let { cast ->
                LabelledText(label = stringResource(string.ui_details_cast), value = cast)
            }
            current.details.director?.let { director ->
                LabelledText(label = stringResource(string.ui_details_director), value = director)
            }
            current.details.plot?.let { plot ->
                Text(
                    text = plot,
                    style = MaterialTheme.typography.bodyMedium,
                    // Long enough to be worth reading, short enough to leave
                    // the actions below reachable without scrolling.
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = spacing.extraSmall),
                )
            }
            HorizontalDivider(modifier = Modifier.padding(top = spacing.small))
        }
    }
}

/** Year, genre and rating on one line — the things read at a glance. */
@Composable
private fun ChannelDetails.Headline() {
    val spacing = LocalSpacing.current
    val year = releaseDate?.take(4)?.takeIf { it.length == 4 && it.all(Char::isDigit) }
    val summary = listOfNotNull(year, genre).joinToString(" · ")
    if (summary.isBlank() && rating == null) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(spacing.small),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (summary.isNotBlank()) {
            Text(
                text = summary,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        rating?.let { rating ->
            Icon(
                imageVector = Icons.Rounded.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = rating,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LabelledText(label: String, value: String) {
    Text(
        text = "$label: $value",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}
