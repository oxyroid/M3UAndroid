package com.m3u.features.feed.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.m3u.data.database.entity.Live
import com.m3u.i18n.R.string
import com.m3u.material.components.Image
import com.m3u.material.components.TextBadge
import com.m3u.material.model.LocalSpacing
import java.net.URI

@Composable
internal fun LiveItem(
    live: Live,
    noPictureMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val spacing = LocalSpacing.current

    val scheme = remember(live) {
        try {
            URI(live.url).scheme
        } catch (ignored: Exception) {
            null
        } ?: context.getString(string.feat_feed_scheme_unknown).uppercase()
    }

    OutlinedCard(Modifier.semantics(mergeDescendants = true) { }) {
        Column(
            modifier = Modifier
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .then(modifier)
        ) {
            AnimatedVisibility(
                visible = !noPictureMode && !live.cover.isNullOrEmpty()
            ) {
                Image(
                    model = live.cover,
                    errorPlaceholder = live.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4 / 3f)
                )
            }
            Column(
                modifier = Modifier.padding(spacing.medium),
                verticalArrangement = Arrangement.spacedBy(spacing.small)
            ) {
                Text(
                    text = live.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontSize = MaterialTheme.typography.titleSmall.fontSize,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall)
                ) {
                    TextBadge(scheme)
                    Text(
                        text = live.url,
                        maxLines = 1,
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
