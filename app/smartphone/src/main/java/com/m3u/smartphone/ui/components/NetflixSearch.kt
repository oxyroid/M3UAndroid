package com.m3u.smartphone.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import coil.compose.AsyncImage
import com.m3u.data.database.model.Channel
import com.m3u.smartphone.ui.material.model.LocalSpacing
import com.m3u.smartphone.ui.material.model.NetflixTheme
import com.m3u.smartphone.ui.material.model.glassmorphismHaze
import com.m3u.smartphone.ui.material.model.scrim
import kotlinx.coroutines.flow.Flow

/**
 * Netflix-style search overlay
 * Full-screen search with live results
 */
@Composable
fun NetflixSearch(
    query: String,
    onQueryChange: (String) -> Unit,
    channels: Flow<PagingData<Channel>>,
    onChannelClick: (Channel) -> Unit,
    onDismiss: () -> Unit,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val focusRequester = remember { FocusRequester() }
    val lazyPagingItems = channels.collectAsLazyPagingItems()

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically(),
        exit = fadeOut() + slideOutVertically()
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            NetflixTheme.NetflixBlack,
                            NetflixTheme.NetflixDarkGray
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Search bar
                SearchBar(
                    query = query,
                    onQueryChange = onQueryChange,
                    onBack = onDismiss,
                    onClear = { onQueryChange("") },
                    focusRequester = focusRequester,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(spacing.medium)
                )

                Spacer(modifier = Modifier.height(spacing.medium))

                // Search results
                if (query.isNotEmpty()) {
                    SearchResults(
                        channels = lazyPagingItems,
                        onChannelClick = onChannelClick,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Empty state
                    EmptySearchState(
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        LaunchedEffect(visible) {
            if (visible) {
                focusRequester.requestFocus()
            }
        }
    }
}

/**
 * Netflix-style search bar
 */
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onClear: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current

    Row(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .glassmorphismHaze(
                backgroundColor = NetflixTheme.NetflixGray.copy(alpha = 0.9f),
                shape = RoundedCornerShape(28.dp)
            )
            .padding(horizontal = spacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.small)
    ) {
        // Back button
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = Color.White
            )
        }

        // Search input
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = Color.White,
                fontWeight = FontWeight.Normal
            ),
            cursorBrush = SolidColor(NetflixTheme.NetflixRed),
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Search
            ),
            keyboardActions = KeyboardActions(
                onSearch = { /* Search triggered */ }
            ),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (query.isEmpty()) {
                        Text(
                            text = "Search movies, TV shows, channels...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                    innerTextField()
                }
            },
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
        )

        // Clear button
        if (query.isNotEmpty()) {
            IconButton(
                onClick = onClear,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Clear,
                    contentDescription = "Clear",
                    tint = Color.White
                )
            }
        } else {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * Search results grid
 */
@Composable
private fun SearchResults(
    channels: LazyPagingItems<Channel>,
    onChannelClick: (Channel) -> Unit,
    modifier: Modifier = Modifier
) {
    val state = rememberLazyStaggeredGridState()
    val spacing = LocalSpacing.current

    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(150.dp),
        state = state,
        contentPadding = PaddingValues(horizontal = spacing.medium),
        horizontalArrangement = Arrangement.spacedBy(spacing.small),
        verticalItemSpacing = spacing.small,
        modifier = modifier
    ) {
        items(channels.itemCount) { index ->
            channels[index]?.let { channel ->
                SearchResultItem(
                    channel = channel,
                    onClick = { onChannelClick(channel) }
                )
            }
        }
    }
}

/**
 * Individual search result item
 */
@Composable
private fun SearchResultItem(
    channel: Channel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current

    Box(
        modifier = modifier
            .width(150.dp)
            .height(220.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        // Thumbnail
        AsyncImage(
            model = channel.cover,
            contentDescription = channel.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
        )

        // Gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .scrim(NetflixTheme.BottomScrim)
        )

        // Title
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(spacing.small),
            verticalArrangement = Arrangement.Bottom
        ) {
            Text(
                text = channel.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (channel.category.isNotEmpty()) {
                Text(
                    text = channel.category,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Empty search state
 */
@Composable
private fun EmptySearchState(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(64.dp)
            )
            Text(
                text = "Search for movies, TV shows, and channels",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.7f)
            )
            Text(
                text = "Start typing to see results",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.5f)
            )
        }
    }
}
