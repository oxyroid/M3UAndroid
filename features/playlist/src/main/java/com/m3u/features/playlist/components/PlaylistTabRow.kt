package com.m3u.features.playlist.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.m3u.features.playlist.Category
import com.m3u.material.components.IconButton
import com.m3u.material.model.LocalSpacing

@Composable
internal fun PlaylistTabRow(
    page: Int,
    onPageChanged: (Int) -> Unit,
    categories: List<Category>,
    pinnedCategories: List<String>,
    onPinOrUnpinCategory: (String) -> Unit,
    onHideCategory: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val hapticFeedback = LocalHapticFeedback.current
    val state = rememberLazyListState()
    Box(modifier) {
        if (categories.size > 1) {
            var focusCategory: String? by rememberSaveable { mutableStateOf(null) }
            Column {
                LazyRow(
                    state = state,
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    stickyHeader {
                        AnimatedContent(
                            targetState = focusCategory,
                            label = "playlist-tab-row-action-buttons",
                            modifier = Modifier.background(
                                MaterialTheme.colorScheme.background
                            )
                        ) { name ->
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
                                    onClick = { /*TODO*/ }
                                )
                            }
                        }
                    }
                    itemsIndexed(categories) { index, channel ->
                        PlaylistTabRowItem(
                            name = channel.name,
                            selected = page == index,
                            pinned = channel.name in pinnedCategories,
                            focused = focusCategory == channel.name,
                            hasOtherFocused = focusCategory != null && focusCategory != channel.name,
                            onClick = {
                                if (focusCategory == null) {
                                    onPageChanged(index)
                                }
                            },
                            onLongClick = {
                                focusCategory = channel.name
                                onPageChanged(index)
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        )
                    }
                }
                HorizontalDivider()
            }
            BackHandler(focusCategory != null) {
                focusCategory = null
            }
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
                else Color.Transparent,
                contentColor = if (focused) MaterialTheme.colorScheme.surfaceVariant
                else LocalContentColor.current
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