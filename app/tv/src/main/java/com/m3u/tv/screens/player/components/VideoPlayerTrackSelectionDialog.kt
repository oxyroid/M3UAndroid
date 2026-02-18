package com.m3u.tv.screens.player.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.m3u.i18n.R
import com.m3u.tv.StandardDialog
import com.m3u.tv.screens.profile.AccountsSectionDialogButton

/**
 * Track selection modal aligned with RFC 8216 (HTTP Live Streaming).
 * - Video: variant streams (resolution, bitrate).
 * - Audio: alternative audio renditions (NAME, LANGUAGE, CHANNELS).
 * - Subtitles: subtitle renditions (NAME, LANGUAGE).
 */
@Composable
fun VideoPlayerTrackSelectionDialog(
    visible: Boolean,
    tracks: Map<Int, List<Format>>,
    selectedFormats: Map<Int, Format?>,
    onDismiss: () -> Unit,
    onChooseTrack: (type: @C.TrackType Int, format: Format) -> Unit,
    onClearTrack: (type: @C.TrackType Int) -> Unit,
) {
    if (!visible) return
    StandardDialog(
        showDialog = true,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feat_channel_dialog_choose_tracks)) },
        textContentColor = MaterialTheme.colorScheme.onSurface,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(R.string.feat_channel_dialog_choose_tracks_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                )
                listOf(
                    C.TRACK_TYPE_VIDEO to "Video quality",
                    C.TRACK_TYPE_AUDIO to "Audio track",
                    C.TRACK_TYPE_TEXT to "Subtitles",
                ).forEach { (type, sectionLabel) ->
                    val formatList = tracks[type].orEmpty()
                    if (formatList.isEmpty()) return@forEach
                    Text(
                        text = sectionLabel,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, bottom = 4.dp),
                    )
                    val selectedFormat = selectedFormats[type]
                    formatList.forEach { format ->
                        val selected = format.id == selectedFormat?.id
                        val displayText = format.displayText(type) +
                            if (selected) " • Selected" else ""
                        androidx.tv.material3.ListItem(
                            selected = selected,
                            headlineContent = {
                                Text(
                                    displayText,
                                    color = LocalContentColor.current,
                                )
                            },
                            onClick = {
                                if (selected) {
                                    onClearTrack(type)
                                } else {
                                    onChooseTrack(type, format)
                                }
                            },
                            colors = ListItemDefaults.colors(
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                focusedContentColor = MaterialTheme.colorScheme.inverseOnSurface,
                                focusedContainerColor = MaterialTheme.colorScheme.inverseSurface,
                            ),
                        )
                    }
                }
            }
        },
        dismissButton = {
            AccountsSectionDialogButton(
                text = stringResource(R.string.feat_channel_dialog_close),
                shouldRequestFocus = false,
                onClick = onDismiss,
            )
        },
        confirmButton = { },
    )
}

/**
 * Human-readable label per RFC 8216 semantics (EXT-X-MEDIA NAME/LANGUAGE/CHANNELS,
 * EXT-X-STREAM-INF RESOLUTION/BANDWIDTH).
 */
private fun Format.displayText(type: @C.TrackType Int): String = when (type) {
    C.TRACK_TYPE_VIDEO -> buildList {
        add("$width×$height")
        if (bitrate != Format.NO_VALUE && bitrate > 0) {
            add("${bitrate / 1_000_000} Mbps")
        }
        sampleMimeType?.takeIf { it.isNotBlank() }?.let { add(it) }
    }.joinToString(separator = " • ")
    C.TRACK_TYPE_AUDIO -> buildList {
        label?.takeIf { it.isNotBlank() }?.let { add(it) }
        language?.takeIf { it.isNotBlank() }?.let { add(it) }
        if (channelCount != Format.NO_VALUE && channelCount > 0) {
            add("${channelCount} ch")
        }
        add("$sampleRate Hz")
        sampleMimeType?.takeIf { it.isNotBlank() }?.let { add(it) }
    }.joinToString(separator = " • ").ifEmpty { "$sampleRate Hz ${sampleMimeType.orEmpty()}" }
    C.TRACK_TYPE_TEXT -> buildList {
        label?.takeIf { it.isNotBlank() }?.let { add(it) }
        language?.takeIf { it.isNotBlank() }?.let { add(it) }
        sampleMimeType?.takeIf { it.isNotBlank() }?.let { add(it) }
    }.joinToString(separator = " • ").ifEmpty { sampleMimeType.orEmpty() }
    else -> sampleMimeType.orEmpty()
}
