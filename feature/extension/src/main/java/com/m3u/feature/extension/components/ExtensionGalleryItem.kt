package com.m3u.feature.extension.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.m3u.extension.api.analyzer.HlsPropAnalyzer
import com.m3u.extension.runtime.Extension
import com.m3u.material.components.SelectionsDefaults
import com.m3u.material.components.ToggleableSelection
import com.m3u.material.model.LocalSpacing
import com.m3u.ui.helper.LocalHelper

@Composable
internal fun ExtensionGalleryItem(
    extension: Extension,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val spacing = LocalSpacing.current
    val helper = LocalHelper.current
    var expanded by remember { mutableStateOf(false) }
    ToggleableSelection(
        checked = expanded,
        onChanged = { expanded = !expanded },
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
    ) {
        Column {
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.medium, Alignment.Start),
                modifier = Modifier.padding(vertical = spacing.small)
            ) {
                AsyncImage(
                    model = extension.icon,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp)
                )
                Column {
                    Text(
                        text = extension.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = extension.packageName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = LocalContentColor.current.copy(0.65f)
                    )
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(bottom = spacing.small)) {
                    extension.analyzers.forEach { analyzer ->
                        when (analyzer) {
                            is HlsPropAnalyzer -> {
                                HlsPropAnalyzerContent(analyzer)
                            }
                        }
                        Spacer(modifier = Modifier.height(spacing.extraSmall))
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(spacing.small),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val actions = listOf(
                            ExtensionAction("已激活", false, {}),
                            ExtensionAction("统计", true, {}),
                            ExtensionAction("卸载", true) {
                                val packageManager = context.packageManager
                                val packageInstaller = packageManager.packageInstaller
                            }
                        )
                        actions.forEach { label ->
                            ExtensionActionButton(label)
                        }
                    }
                }
            }
        }
    }
}

internal data class ExtensionAction(
    val label: String,
    val enabled: Boolean,
    val onClick: () -> Unit
)

@Composable
private fun RowScope.ExtensionActionButton(action: ExtensionAction) {
    val spacing = LocalSpacing.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(SelectionsDefaults.Shape)
            .weight(1f)
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(0.dp))
            .clickable(onClick = action.onClick, enabled = action.enabled)
            .padding(spacing.small)
    ) {
        Text(
            text = action.label,
            color = MaterialTheme.colorScheme.onSurface.copy(
                alpha = if (action.enabled) 1f else 0.65f
            ),
            maxLines = 1
        )
    }
}

@Composable
private fun HlsPropAnalyzerContent(
    analyzer: HlsPropAnalyzer,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        val description = remember(analyzer.description) {
            analyzer.description.replace("\n+".toRegex(), " ")
        }
        Text(
            text = analyzer.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = LocalContentColor.current.copy(0.65f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }

}