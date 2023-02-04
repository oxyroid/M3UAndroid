package com.m3u.features.favorite.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Link
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import com.m3u.data.entity.Live
import com.m3u.features.favorite.R
import com.m3u.ui.components.M3UColumn
import com.m3u.ui.model.LocalSpacing
import com.m3u.ui.model.LocalTheme
import java.net.URI

@Composable
internal fun FavoriteLiveItem(
    live: Live,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scheme = remember(live) {
        URI(live.url).scheme ?: context.getString(R.string.scheme_unknown).uppercase()
    }
    Card(
        shape = RectangleShape,
        backgroundColor = LocalTheme.current.surface,
        contentColor = LocalTheme.current.onSurface
    ) {
        M3UColumn(
            modifier = modifier
                .clickable(
                    onClick = onClick
                )
        ) {
            CompositionLocalProvider(
                LocalContentColor provides LocalTheme.current.onSecondary
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(LocalSpacing.current.extraSmall)
                ) {
                    BrightIcon(
                        text = title,
                        color = LocalTheme.current.secondary,
                        contentColor = LocalTheme.current.onSecondary,
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.Link,
                                contentDescription = null,
                                modifier = Modifier.size(LocalSpacing.current.medium)
                            )
                        }
                    )
                    Text(
                        text = live.title,
                        style = MaterialTheme.typography.body1,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(LocalSpacing.current.extraSmall)
            ) {
                BrightIcon(
                    text = scheme,
                    color = LocalTheme.current.primary,
                    contentColor = LocalTheme.current.onPrimary
                )
                CompositionLocalProvider(
                    LocalContentAlpha provides 0.6f
                ) {
                    Text(
                        text = live.url,
                        maxLines = 1,
                        style = MaterialTheme.typography.body2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
