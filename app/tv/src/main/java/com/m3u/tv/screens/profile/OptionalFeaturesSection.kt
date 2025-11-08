package com.m3u.tv.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import com.m3u.core.architecture.preferences.PreferencesKeys
import com.m3u.core.architecture.preferences.mutablePreferenceOf
import com.m3u.core.util.basic.title
import com.m3u.i18n.R

/**
 * Optional features section in Settings.
 * Contains adult content access with PIN protection indicator.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun OptionalFeaturesSection(
    onNavigateToAdultContent: () -> Unit,
    modifier: Modifier = Modifier
) {
    var contentTypeMode by mutablePreferenceOf(PreferencesKeys.CONTENT_TYPE_MODE)

    Column(modifier = modifier.padding(horizontal = 72.dp)) {
        Text(
            text = stringResource(R.string.feat_setting_optional_features).title(),
            style = MaterialTheme.typography.headlineSmall
        )

        Text(
            modifier = Modifier
                .graphicsLayer { alpha = 0.8f }
                .padding(top = 16.dp),
            text = "Additional features that are protected and require PIN access",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Box(
            modifier = Modifier
                .padding(top = 16.dp)
                .fillMaxWidth()
                .height(2.dp)
                .background(MaterialTheme.colorScheme.border.copy(alpha = 0.6f))
        )

        // Adult Content Feature
        ListItem(
            selected = false,
            onClick = onNavigateToAdultContent,
            leadingContent = {
                Icon(
                    imageVector = Icons.Rounded.Movie,
                    contentDescription = "Adult Content",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            headlineContent = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Adult Content",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )

                    // PIN protection indicator
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                                MaterialTheme.shapes.small
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = "PIN Protected",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "PIN Required",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            supportingContent = {
                Text(
                    text = "Browse and watch content from your favorite models",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            },
            colors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface,
                focusedContainerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        )

        // Age warning text
        Text(
            modifier = Modifier
                .graphicsLayer { alpha = 0.6f }
                .padding(top = 24.dp),
            text = "⚠️ Age Restriction: This feature is intended for users 21 years and older. Access requires PIN authentication.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.error
        )

        // Content Type Mode Toggle
        Box(
            modifier = Modifier
                .padding(top = 32.dp)
                .fillMaxWidth()
                .height(2.dp)
                .background(MaterialTheme.colorScheme.border.copy(alpha = 0.6f))
        )

        ListItem(
            selected = false,
            onClick = { contentTypeMode = !contentTypeMode },
            leadingContent = {
                Icon(
                    imageVector = Icons.Rounded.Category,
                    contentDescription = "Content Type Mode",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            headlineContent = {
                Text(
                    text = "Content Type Mode",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
            },
            supportingContent = {
                Column(modifier = Modifier.padding(top = 4.dp)) {
                    Text(
                        text = if (contentTypeMode) "Enabled - Shows Live TV, Movies, and Series categories"
                               else "Disabled - Shows traditional playlist groups",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        modifier = Modifier.padding(top = 8.dp),
                        text = "⚠️ Only enable if you use Xtream Codes playlists",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            trailingContent = {
                Switch(
                    checked = contentTypeMode,
                    onCheckedChange = { contentTypeMode = it }
                )
            },
            colors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface,
                focusedContainerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        )
    }
}
