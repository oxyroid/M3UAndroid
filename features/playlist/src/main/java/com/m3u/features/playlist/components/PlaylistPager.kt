package com.m3u.features.playlist.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.m3u.data.database.model.Stream
import com.m3u.features.playlist.Channel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch

@Composable
internal fun PlaylistPager(
    channels: ImmutableList<Channel>,
    modifier: Modifier = Modifier,
    content: @Composable (streams: ImmutableList<Stream>) -> Unit,
) {
    Column(modifier) {
        val pagerState = rememberPagerState { channels.size }
        val coroutineScope = rememberCoroutineScope()
        Column(Modifier.animateContentSize()) {
            if (channels.size > 1) {
                PrimaryScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = Color.Transparent,
                    indicator = { tabPositions ->
                        val index = pagerState.currentPage
                        with(TabRowDefaults) {
                            Modifier.tabIndicatorOffset(
                                currentTabPosition = tabPositions[index]
                            )
                        }
                    },
                    tabs = {
                        channels.forEachIndexed { index, channel ->
                            val selected = pagerState.currentPage == index
                            Tab(
                                selected = selected,
                                onClick = {
                                    coroutineScope.launch {
                                        pagerState.scrollToPage(index)
                                    }
                                },
                                text = {
                                    Text(
                                        text = channel.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (selected) MaterialTheme.colorScheme.onBackground
                                        else Color.Unspecified,
                                        fontWeight = FontWeight.Bold.takeIf { selected }
                                    )
                                }
                            )
                        }
                    },
                    divider = {},
                    modifier = Modifier.fillMaxWidth()
                )
                HorizontalDivider()
            }
        }
        HorizontalPager(pagerState) { pager ->
            content(
                // we need stable list here
                channels[pager].streams
            )
        }
    }
}
