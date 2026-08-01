package com.m3u.smartphone.ui.business.playlist.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.m3u.core.foundation.components.AbsoluteSmoothCornerShape
import com.m3u.i18n.R.string
import com.m3u.smartphone.ui.material.ktx.Edge
import com.m3u.smartphone.ui.material.ktx.blurEdge
import com.m3u.smartphone.ui.material.ktx.safeDisplayText
import com.m3u.smartphone.ui.material.model.LocalHazeState
import com.m3u.smartphone.ui.material.model.LocalSpacing
import dev.chrisbanes.haze.hazeSource

@Composable
internal fun PlaylistTabRow(
    selectedCategory: String,
    categories: List<String>,
    isExpanded: Boolean,
    bottomContentPadding: PaddingValues,
    onCategoryChanged: (String) -> Unit,
    pinnedCategories: List<String>,
    onPinOrUnpinCategory: (String) -> Unit,
    onHideCategory: (String) -> Unit,
    onExpanded: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val hapticFeedback = LocalHapticFeedback.current
    val state = rememberLazyListState()
    val pinDescription = stringResource(string.ui_action_pin)
    val unpinDescription = stringResource(string.ui_action_unpin)
    val hideDescription = stringResource(string.ui_action_hide)
    val categoryOptionsDescription = stringResource(string.ui_action_category_options)
    val expandDescription = stringResource(string.ui_action_expand_categories)
    val collapseDescription = stringResource(string.ui_action_collapse_categories)
    val manageCategoriesTitle = stringResource(string.feat_playlist_manage_categories)
    val expandedStateDescription = stringResource(string.ui_state_expanded)
    val collapsedStateDescription = stringResource(string.ui_state_collapsed)
    val pinnedStateDescription = stringResource(string.ui_state_pinned)
    val categoryMenuSemantics = categoryMenuSemantics(
        isExpanded = isExpanded,
        expandedStateDescription = expandedStateDescription,
        collapsedStateDescription = collapsedStateDescription,
        expandActionLabel = expandDescription,
        collapseActionLabel = collapseDescription,
    )

    Box(modifier) {
        val categoryMenuModifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clearAndSetSemantics {
                contentDescription = categoryOptionsDescription
                role = Role.Button
                stateDescription = categoryMenuSemantics.stateDescription
                onClick(label = categoryMenuSemantics.actionLabel) {
                    onExpanded()
                    true
                }
            }
        val header = @Composable {
            if (isExpanded) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(start = spacing.medium),
                ) {
                    Text(
                        text = manageCategoriesTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        modifier = categoryMenuModifier,
                        onClick = onExpanded,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = null,
                        )
                    }
                }
            } else {
                IconButton(
                    modifier = categoryMenuModifier,
                    onClick = onExpanded,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Menu,
                        contentDescription = null,
                    )
                }
            }
        }
        LaunchedEffect(selectedCategory, isExpanded) {
            val index = categories.indexOf(selectedCategory)
            if (index != -1) {
                state.animateScrollToItem(index + 1)
            }
        }
        Column {
            if (isExpanded) {
                LazyColumn(
                    state = state,
                    horizontalAlignment = Alignment.Start,
                    contentPadding = bottomContentPadding,
                    modifier = Modifier
                        .fillMaxSize()
                        .selectableGroup()
                        .background(MaterialTheme.colorScheme.surface)
                        .hazeSource(LocalHazeState.current),
                ) {
                    stickyHeader { header() }
                    items(categories, key = { it }) { category ->
                        val pinned = category in pinnedCategories
                        ExpandedPlaylistCategoryItem(
                            name = category,
                            selected = category == selectedCategory,
                            pinned = pinned,
                            pinDescription = if (pinned) unpinDescription else pinDescription,
                            hideDescription = hideDescription,
                            pinnedStateDescription = pinnedStateDescription,
                            onClick = { onCategoryChanged(category) },
                            onPin = { onPinOrUnpinCategory(category) },
                            onHide = { onHideCategory(category) },
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = spacing.medium),
                        )
                    }
                }
            } else {
                LazyRow(
                    state = state,
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall),
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface)
                        .blurEdge(MaterialTheme.colorScheme.surface, Edge.End)
                        .fillMaxWidth()
                        .selectableGroup(),
                ) {
                    stickyHeader { header() }
                    items(categories, key = { it }) { category ->
                        CollapsedPlaylistCategoryItem(
                            name = category,
                            selected = category == selectedCategory,
                            pinned = category in pinnedCategories,
                            onClick = { onCategoryChanged(category) },
                            onLongClick = {
                                onCategoryChanged(category)
                                onExpanded()
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            categoryOptionsDescription = categoryOptionsDescription,
                            pinnedStateDescription = pinnedStateDescription,
                        )
                    }
                }
            }
            HorizontalDivider()
        }
    }
}

internal data class CategoryMenuSemantics(
    val stateDescription: String,
    val actionLabel: String,
)

internal fun categoryMenuSemantics(
    isExpanded: Boolean,
    expandedStateDescription: String,
    collapsedStateDescription: String,
    expandActionLabel: String,
    collapseActionLabel: String,
): CategoryMenuSemantics = if (isExpanded) {
    CategoryMenuSemantics(
        stateDescription = expandedStateDescription,
        actionLabel = collapseActionLabel,
    )
} else {
    CategoryMenuSemantics(
        stateDescription = collapsedStateDescription,
        actionLabel = expandActionLabel,
    )
}

@Composable
private fun ExpandedPlaylistCategoryItem(
    name: String,
    selected: Boolean,
    pinned: Boolean,
    pinDescription: String,
    hideDescription: String,
    pinnedStateDescription: String,
    onClick: () -> Unit,
    onPin: () -> Unit,
    onHide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    ListItem(
        headlineContent = {
            Text(
                text = name.safeDisplayText(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (selected || pinned) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Row {
                IconButton(
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                    onClick = onPin,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PushPin,
                        contentDescription = pinDescription,
                        tint = if (pinned) contentColor else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                IconButton(
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                    onClick = onHide,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.VisibilityOff,
                        contentDescription = hideDescription,
                        tint = if (selected) contentColor else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = containerColor,
            headlineColor = contentColor,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = onClick,
            )
            .semantics {
                if (pinned) {
                    stateDescription = pinnedStateDescription
                }
            }
            .then(modifier),
    )
}

@Composable
private fun CollapsedPlaylistCategoryItem(
    name: String,
    selected: Boolean,
    pinned: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    categoryOptionsDescription: String,
    pinnedStateDescription: String,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = if (selected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        ),
        shape = shape,
        modifier = Modifier
            .clip(shape)
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .semantics {
                this.selected = selected
                if (pinned) {
                    stateDescription = pinnedStateDescription
                }
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick,
                onLongClickLabel = categoryOptionsDescription,
                onLongClick = onLongClick,
                role = Role.Tab,
            )
            .then(modifier),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .padding(
                        start = spacing.medium,
                        end = spacing.medium,
                        top = spacing.small,
                    )
                    .heightIn(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.widthIn(max = 240.dp),
                ) {
                    if (pinned) {
                        Icon(
                            imageVector = Icons.Rounded.PushPin,
                            contentDescription = null,
                            modifier = Modifier.requiredSize(16.dp),
                        )
                    }
                    Text(
                        text = name.safeDisplayText(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (pinned || selected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Box(
                Modifier
                    .requiredSize(48.dp, spacing.small)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = AbsoluteSmoothCornerShape(
                            cornerRadiusTL = 4.dp,
                            cornerRadiusTR = 4.dp,
                            smoothnessAsPercentBL = 60,
                        ),
                    )
            )
        }
    }
}
