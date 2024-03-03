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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.m3u.features.playlist.Category
import com.m3u.material.components.IconButton
import com.m3u.material.model.LocalSpacing
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch

@Composable
internal fun PlaylistTabRow(
    page: Int,
    onPageChanged: (Int) -> Unit,
    pinnedCategories: ImmutableList<String>,
    onPinOrUnpinCategory: (String) -> Unit,
    onHideCategory: (String) -> Unit,
    categories: ImmutableList<Category>,
    modifier: Modifier = Modifier
) {
    Box(modifier) {
        if (categories.size > 1) {
            val hapticFeedback = LocalHapticFeedback.current
            var focusCategory: String? by rememberSaveable { mutableStateOf(null) }
            Column {
                val state = rememberLazyListState()
                val coroutineScope = rememberCoroutineScope()
                LazyRow(
                    state = state,
                    verticalAlignment = Alignment.CenterVertically,
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
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                coroutineScope.launch {
                                    state.animateScrollToItem(index)
                                }
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
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val interactionSource = remember { MutableInteractionSource() }

    CompositionLocalProvider(
        LocalContentColor provides LocalContentColor.current.copy(
            when {
                focused -> 1f
                hasOtherFocused -> 0.25f
                selected -> 0.8f
                else -> 0.65f
            }
        )
    ) {
        val indication = if (hasOtherFocused) null else rememberRipple()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.small),
            modifier = Modifier
                .padding(vertical = spacing.extraSmall)
                .clip(MaterialTheme.shapes.medium)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = indication,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    role = Role.Tab
                )
                .padding(horizontal = spacing.medium)
                .minimumInteractiveComponentSize()
                .then(modifier)
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall.copy(
                    textDecoration = if (pinned) TextDecoration.Underline else TextDecoration.None
                ),
                fontWeight = FontWeight.Bold.takeIf { (selected && !hasOtherFocused) || focused }
            )
        }
    }
}