package com.m3u.features.playlist.components

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.m3u.material.components.IconButton
import com.m3u.material.ktx.thenIf
import com.m3u.material.model.LocalHazeState
import com.m3u.material.model.LocalSpacing
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
                    )
            ) {
                stickyHeader {
                    header()
                    HorizontalDivider()
                }
                items(categories) { category ->
                    PlaylistTabRowItem(
                        name = category,
                        selected = category == selectedCategory,
                        pinned = category in pinnedCategories,
                        focused = category == focusCategory,
                        hasOtherFocused = focusCategory != null && focusCategory != category,
                        onClick = {
                            if (focusCategory == null) {
                                onCategoryChanged(category)
                            }
                        },
                        onLongClick = {
                            focusCategory = category
                            onCategoryChanged(category)
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        } else {
            Column {
                LazyRow(
                    state = state,
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall),
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface)
                        .fillMaxWidth()
                ) {
                    stickyHeader { header() }
                    items(categories) { category ->
                        PlaylistTabRowItem(
                            name = category,
                            selected = category == selectedCategory,
                            pinned = category in pinnedCategories,
                            focused = category == focusCategory,
                            hasOtherFocused = focusCategory != null && focusCategory != category,
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
                HorizontalDivider()
            }
        }
        BackHandler(focusCategory != null) {
            focusCategory = null
        }
    }
}

@Composable
private fun PlaylistTabRowItem(
    name: String,
    selected: Boolean,
    pinned: Boolean,
    focused: Boolean,
    hasOtherFocused: Boolean,
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
        val indication = if (hasOtherFocused) null else rememberRipple()
        val shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
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
                .then(modifier)
        ) {
            Box(
                modifier = Modifier
                    .padding(
                        horizontal = spacing.medium,
                        vertical = spacing.small
                    )
                    .heightIn(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall.copy(
                        textDecoration = if (pinned) TextDecoration.Underline else TextDecoration.None
                    ),
                    fontWeight = when {
                        focused -> FontWeight.Black
                        selected && !hasOtherFocused -> FontWeight.Bold
                        else -> null
                    }
                )
            }
        }
    }
}