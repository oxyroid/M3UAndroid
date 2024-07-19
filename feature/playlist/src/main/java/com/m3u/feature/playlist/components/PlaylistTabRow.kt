package com.m3u.feature.playlist.components

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.m3u.material.components.IconButton
import com.m3u.material.effects.BackStackEntry
import com.m3u.material.effects.BackStackHandler
import com.m3u.material.ktx.Edge
import com.m3u.material.ktx.blurEdge
import com.m3u.material.ktx.thenIf
import com.m3u.material.model.LocalHazeState
import com.m3u.material.model.LocalSpacing
import com.m3u.material.shape.AbsoluteSmoothCornerShape
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.haze

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

    Box(modifier) {
        var focusCategory: String? by rememberSaveable { mutableStateOf(null) }
        val header = @Composable {
            AnimatedContent(
                targetState = focusCategory,
                label = "playlist-tab-row-action-buttons",
                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
            ) { name ->
                Box(
                    modifier = Modifier.thenIf(isExpanded) {
                        Modifier.fillMaxWidth()
                    }
                ) {
                    if (name != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End
                        ) {
                            IconButton(
                                icon = Icons.Rounded.PushPin,
                                contentDescription = "pin",
                                onClick = {
                                    name.let(onPinOrUnpinCategory)
                                    focusCategory = null
                                }
                            )
                            IconButton(
                                icon = Icons.Rounded.VisibilityOff,
                                contentDescription = "hide",
                                onClick = {
                                    name.let(onHideCategory)
                                    focusCategory = null
                                }
                            )
                        }
                    } else {
                        IconButton(
                            icon = Icons.Rounded.Menu,
                            contentDescription = "",
                            onClick = onExpanded
                        )
                    }
                }
            }
        }
        LaunchedEffect(Unit) {
            val index = categories.indexOf(selectedCategory)
            if (index != -1) {
                state.scrollToItem(index)
            }
        }
        val categoriesContent: LazyListScope.() -> Unit = {
            stickyHeader { header() }
            items(categories) { category ->
                PlaylistTabRowItem(
                    name = category,
                    selected = category == selectedCategory,
                    pinned = category in pinnedCategories,
                    focused = category == focusCategory,
                    hasOtherFocused = focusCategory != null && focusCategory != category,
                    isExpanded = isExpanded,
                    onClick = {
                        if (focusCategory == null) {
                            onCategoryChanged(category)
                        }
                    },
                    onLongClick = {
                        focusCategory = category
                        onCategoryChanged(category)
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                )
            }
        }
        Column {
            if (isExpanded) {
                LazyColumn(
                    state = state,
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(spacing.extraSmall),
                    contentPadding = bottomContentPadding,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                        .haze(
                            LocalHazeState.current,
                            HazeDefaults.style(MaterialTheme.colorScheme.surface)
                        ),
                    content = categoriesContent
                )
            } else {
                LazyRow(
                    state = state,
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall),
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface)
                        .blurEdge(MaterialTheme.colorScheme.surface, Edge.End)
                        .fillMaxWidth(),
                    content = categoriesContent
                )
            }
            HorizontalDivider()
        }
        BackStackHandler(
            enabled = focusCategory != null,
            entry = BackStackEntry(Icons.Rounded.Close),
            onBack = { focusCategory = null }
        )
    }
}

@Composable
private fun PlaylistTabRowItem(
    name: String,
    selected: Boolean,
    pinned: Boolean,
    focused: Boolean,
    hasOtherFocused: Boolean,
    isExpanded: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val interactionSource = remember { MutableInteractionSource() }

    CompositionLocalProvider(
        LocalContentColor provides LocalContentColor.current.copy(
            when {
                focused -> 1f
                hasOtherFocused -> 0.25f
                selected -> 1f
                else -> 0.65f
            }
        )
    ) {
        val indication = if (hasOtherFocused) null else ripple()
        val shape = if (isExpanded) RectangleShape
        else RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (focused) LocalContentColor.current
                else MaterialTheme.colorScheme.surface,
                contentColor = if (focused) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.onSurface
            ),
            shape = shape,
            modifier = Modifier
                .clip(shape)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = indication,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    role = Role.Tab
                )
                .thenIf(isExpanded) { Modifier.fillMaxWidth() }
                .then(modifier)
        ) {
            val text = @Composable {
                Box(
                    modifier = Modifier
                        .padding(
                            start = spacing.medium,
                            end = spacing.medium,
                            top = spacing.small,
                            bottom = if (isExpanded) spacing.small else spacing.none
                        )
                        .heightIn(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when {
                            pinned -> "[$name]"
                            else -> name
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = when {
                            pinned -> FontWeight.Black
                            selected && !hasOtherFocused -> FontWeight.Bold
                            else -> null
                        }
                    )
                }
            }
            val indicator = @Composable {
                if (isExpanded) {
                    Box(
                        Modifier
                            .requiredSize(spacing.small)
                            .background(
                                when {
                                    focused -> LocalContentColor.current
                                    selected -> MaterialTheme.colorScheme.primary
                                    else -> Color.Transparent
                                },
                                shape = AbsoluteSmoothCornerShape(
                                    cornerRadiusTL = 4.dp,
                                    cornerRadiusTR = 4.dp,
                                    cornerRadiusBL = 4.dp,
                                    cornerRadiusBR = 4.dp,
                                    smoothnessAsPercentBL = 60
                                )
                            )
                    )
                } else {
                    Box(
                        Modifier
                            .requiredSize(48.dp, spacing.small)
                            .background(
                                when {
                                    focused -> LocalContentColor.current
                                    selected -> MaterialTheme.colorScheme.primary
                                    else -> Color.Transparent
                                },
                                shape = AbsoluteSmoothCornerShape(
                                    cornerRadiusTL = 4.dp,
                                    cornerRadiusTR = 4.dp,
                                    smoothnessAsPercentBL = 60
                                )
                            )
                    )
                }
            }

            if (isExpanded) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = spacing.medium)
                ) {
                    text()
                    indicator()
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    text()
                    indicator()
                }
            }
        }
    }
}