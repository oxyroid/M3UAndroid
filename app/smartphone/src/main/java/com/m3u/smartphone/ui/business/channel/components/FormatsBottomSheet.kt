package com.m3u.smartphone.ui.business.channel.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.DeviceUnknown
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import com.m3u.data.service.PlayerTrack
import com.m3u.i18n.R.string
import com.m3u.smartphone.ui.material.components.mask.MaskState
import com.m3u.smartphone.ui.material.model.LocalSpacing
import kotlinx.coroutines.launch

@Composable
internal fun FormatsBottomSheet(
    visible: Boolean,
    tracks: Map<Int, List<PlayerTrack>>,
    selectedTracks: Map<Int, PlayerTrack?>,
    maskState: MaskState,
    onDismiss: () -> Unit,
    onChooseTrack: (PlayerTrack) -> Unit,
    onClearTrack: (@C.TrackType Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val state = rememberModalBottomSheetState()
    val pagerState = rememberPagerState { tracks.size }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(visible) {
        if (visible) state.show()
        else state.hide()
    }
    if (visible) {
        ModalBottomSheet(
            sheetState = state,
            onDismissRequest = onDismiss,
            modifier = modifier,
//            windowInsets = WindowInsets(0)
        ) {
            LaunchedEffect(Unit) {
                maskState.sleep()
            }
            Text(
                text = stringResource(string.feat_channel_dialog_choose_tracks),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = spacing.medium)
            )
            Spacer(modifier = Modifier.height(spacing.medium))
            val typesIndexed = remember(tracks) {
                tracks.map { it.key }
            }
            val tracksIndexed = remember(tracks) {
                tracks.map { it.value }
            }
            val selectedTracksIndexed = remember(typesIndexed, selectedTracks) {
                typesIndexed.map { selectedTracks[it] }
            }
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = false,
                verticalAlignment = Alignment.Top,
                modifier = Modifier.height(240.dp)
            ) { page ->
                val type = typesIndexed[page]
                val currentTracks = tracksIndexed[page]
                val selectedTrack = selectedTracksIndexed[page]
                LazyColumn(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(
                        items = currentTracks,
                        key = { "${it.group.hashCode()}-${it.index}" },
                        contentType = { "track" }
                    ) { track ->
                        val selected = selectedTrack != null &&
                                track.group == selectedTrack.group &&
                                track.index == selectedTrack.index
                        FormatItem(
                            track = track,
                            type = type,
                            selected = selected,
                            onClick = {
                                if (selected) {
                                    onClearTrack(type)
                                } else {
                                    onChooseTrack(track)
                                }
                            }
                        )
                    }
                }
            }

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.medium, vertical = spacing.small)
            ) {
                tracks.entries.forEachIndexed { index, (type, _) ->
                    val icon = when (type) {
                        C.TRACK_TYPE_AUDIO -> Icons.Rounded.Audiotrack
                        C.TRACK_TYPE_VIDEO -> Icons.Rounded.VideoLibrary
                        C.TRACK_TYPE_TEXT -> Icons.Rounded.ClosedCaption
                        else -> Icons.Rounded.DeviceUnknown
                    }
                    val text = when (type) {
                        C.TRACK_TYPE_AUDIO -> stringResource(string.feat_channel_track_type_audio)
                        C.TRACK_TYPE_VIDEO -> stringResource(string.feat_channel_track_type_video)
                        C.TRACK_TYPE_TEXT -> stringResource(string.feat_channel_track_type_text)
                        else -> stringResource(string.feat_channel_track_type_other)
                    }
                    SegmentedButton(
                        selected = index == pagerState.currentPage,
                        onClick = {
                            coroutineScope.launch {
                                pagerState.scrollToPage(index)
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(
                            baseShape = RoundedCornerShape(8.dp),
                            index = index,
                            count = tracks.size
                        ),
                        colors = SegmentedButtonDefaults.colors(
                            disabledInactiveContentColor = LocalContentColor.current.copy(0.38f)
                        ),
                        icon = {
                            Icon(
                                imageVector = icon,
                                contentDescription = text
                            )
                        },
                        label = { Text(text) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
