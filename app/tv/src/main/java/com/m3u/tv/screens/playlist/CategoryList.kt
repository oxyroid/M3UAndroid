package com.m3u.tv.screens.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

@Composable
fun CategoryList(
    categories: List<String>,
    pinnedCategories: List<String>,
    selectedCategory: String?,
    onCategorySelected: (String) -> Unit,
    onPinOrUnpinCategory: (String) -> Unit,
    onHideCategory: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // ENTERPRISE SMOOTH SCROLLING: Only scroll when item is near edges
    // This prevents jarring jumps and provides smooth navigation
    LaunchedEffect(selectedCategory) {
        selectedCategory?.let { selected ->
            val index = categories.indexOf(selected)
            if (index >= 0) {
                val layoutInfo = listState.layoutInfo
                val visibleItems = layoutInfo.visibleItemsInfo

                if (visibleItems.isEmpty()) {
                    // List not yet laid out, do initial scroll
                    listState.scrollToItem(index)
                } else {
                    val viewportStart = layoutInfo.viewportStartOffset
                    val viewportEnd = layoutInfo.viewportEndOffset
                    val viewportSize = viewportEnd - viewportStart

                    // Define threshold zones (20% from top/bottom)
                    val scrollThreshold = (viewportSize * 0.2f).toInt()
                    val topThreshold = viewportStart + scrollThreshold
                    val bottomThreshold = viewportEnd - scrollThreshold

                    // Find if current item is visible
                    val currentItem = visibleItems.find { it.index == index }

                    if (currentItem == null) {
                        // Item not visible at all, scroll it into view
                        // Use smooth scroll to center position
                        listState.animateScrollToItem(index)
                    } else {
                        // Item is visible, check if it's in threshold zone
                        val itemTop = currentItem.offset
                        val itemBottom = currentItem.offset + currentItem.size

                        when {
                            // Item is too close to top edge, scroll up a bit
                            itemTop < topThreshold -> {
                                val itemsToScroll = ((topThreshold - itemTop) / currentItem.size) + 1
                                val targetIndex = (index - itemsToScroll).coerceAtLeast(0)
                                listState.animateScrollToItem(targetIndex)
                            }
                            // Item is too close to bottom edge, scroll down a bit
                            itemBottom > bottomThreshold -> {
                                val itemsToScroll = ((itemBottom - bottomThreshold) / currentItem.size) + 1
                                val targetIndex = (index + itemsToScroll).coerceAtMost(categories.size - 1)
                                listState.animateScrollToItem(targetIndex)
                            }
                            // Item is comfortably in view, no scroll needed
                            else -> {
                                // No scrolling needed - this prevents the jump!
                            }
                        }
                    }
                }
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 16.dp, horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(categories, key = { it }) { category ->
            CategoryListItem(
                category = category,
                isPinned = category in pinnedCategories,
                isSelected = category == selectedCategory,
                onCategorySelected = { onCategorySelected(category) },
                onPinOrUnpin = { onPinOrUnpinCategory(category) },
                onHide = { onHideCategory(category) }
            )
        }
    }
}

@Composable
private fun CategoryListItem(
    category: String,
    isPinned: Boolean,
    isSelected: Boolean,
    onCategorySelected: () -> Unit,
    onPinOrUnpin: () -> Unit,
    onHide: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(false) }

    // Auto-select category when focused + show controls
    LaunchedEffect(isFocused) {
        if (isFocused) {
            onCategorySelected()
        }
        showControls = isFocused
    }

    ListItem(
        selected = isSelected,
        onClick = onCategorySelected,
        headlineContent = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = category,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected || isFocused) Color.White else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (showControls) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Pin/Unpin button
                        IconButton(
                            onClick = onPinOrUnpin,
                            colors = IconButtonDefaults.colors(
                                containerColor = if (isPinned)
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                else
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                                contentColor = if (isPinned)
                                    MaterialTheme.colorScheme.onPrimary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.padding(0.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PushPin,
                                contentDescription = "Pin category",
                            )
                        }

                        // Hide button
                        IconButton(
                            onClick = onHide,
                            modifier = Modifier.padding(0.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.VisibilityOff,
                                contentDescription = "Hide category"
                            )
                        }
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
            focusedSelectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            containerColor = Color.Transparent,
        ),
        modifier = modifier.onFocusChanged { isFocused = it.isFocused || it.hasFocus }
    )
}
