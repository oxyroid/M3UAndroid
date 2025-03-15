package com.m3u.tv.screens.playlist

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import com.m3u.data.database.model.Channel
import com.m3u.data.service.MediaCommand
import com.m3u.tv.common.Error
import com.m3u.tv.common.Loading
import com.m3u.tv.common.ChannelsRow
import com.m3u.tv.screens.dashboard.rememberChildPadding
import com.m3u.tv.utils.LocalHelper
import kotlinx.coroutines.launch

object ChannelScreen {
    const val ChannelIdBundleKey = "channelId"
}

@Composable
fun ChannelDetailScreen(
    goToChannelPlayer: () -> Unit,
    onBackPressed: () -> Unit,
    refreshScreenWithNewChannel: (Channel) -> Unit,
    channelScreenViewModel: ChannelScreenViewModel = hiltViewModel()
) {
    val helper = LocalHelper.current
    val coroutineScope = rememberCoroutineScope()
    val uiState by channelScreenViewModel.uiState.collectAsStateWithLifecycle()

    when (val s = uiState) {
        is ChannelScreenUiState.Loading -> {
            Loading(modifier = Modifier.fillMaxSize())
        }

        is ChannelScreenUiState.Error -> {
            Error(modifier = Modifier.fillMaxSize())
        }

        is ChannelScreenUiState.Done -> {
            Details(
                channel = s.channel,
                goToChannelPlayer = {
                    coroutineScope.launch {
                        helper.play(MediaCommand.Common(s.channel.id))
                    }
                    goToChannelPlayer()
                },
                onBackPressed = onBackPressed,
                refreshScreenWithNewChannel = refreshScreenWithNewChannel,
                modifier = Modifier
                    .fillMaxSize()
                    .animateContentSize()
            )
        }

        null -> {}
    }
}

@Composable
private fun Details(
    channel: Channel,
    goToChannelPlayer: () -> Unit,
    onBackPressed: () -> Unit,
    refreshScreenWithNewChannel: (Channel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val childPadding = rememberChildPadding()

    BackHandler(onBack = onBackPressed)
    LazyColumn(
        contentPadding = PaddingValues(bottom = 135.dp),
        modifier = modifier,
    ) {
        item {
            ChannelDetail(
                channel = channel,
                goToChannelPlayer = goToChannelPlayer
            )
        }

        item {
            ChannelsRow(
                title = channel.title,
                titleStyle = MaterialTheme.typography.titleMedium,
                channels = emptyList(),
                onChannelSelected = refreshScreenWithNewChannel
            )
        }

        item {
            ChannelReviews(
                modifier = Modifier.padding(top = childPadding.top),
                reviewsAndRatings = emptyList()
            )
        }

        item {
            Box(
                modifier = Modifier
                    .padding(horizontal = childPadding.start)
                    .padding(BottomDividerPadding)
                    .fillMaxWidth()
                    .height(1.dp)
                    .alpha(0.15f)
                    .background(MaterialTheme.colorScheme.onSurface)
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = childPadding.start),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val itemModifier = Modifier.width(192.dp)

                TitleValueText(
                    modifier = itemModifier,
                    title = "LIVE",
                    value = "channel.status"
                )
//                TitleValueText(
//                    modifier = itemModifier,
//                    title = "stringResource(R.string.original_language)",
//                    value = "channel.originalLanguage"
//                )
//                TitleValueText(
//                    modifier = itemModifier,
//                    title = "stringResource(R.string.budget)",
//                    value = "channel.budget"
//                )
//                TitleValueText(
//                    modifier = itemModifier,
//                    title = "stringResource(R.string.revenue)",
//                    value = "channel.revenue"
//                )
            }
        }
    }
}

private val BottomDividerPadding = PaddingValues(vertical = 48.dp)
