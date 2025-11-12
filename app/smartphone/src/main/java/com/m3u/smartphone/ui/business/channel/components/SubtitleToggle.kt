package com.m3u.smartphone.ui.business.channel.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.ClosedCaptionDisabled
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Format
import com.m3u.smartphone.ui.material.model.LocalSpacing
import com.m3u.smartphone.ui.material.model.NetflixTheme
import com.m3u.smartphone.ui.material.model.glassmorphismHaze

/**
 * Quick subtitle toggle button for player controls
 * Netflix-style with glassmorphism effect
 */
@Composable
fun SubtitleToggleButton(
    formats: Map<Int, List<Format>>,
    selectedFormats: Map<Int, Format?>,
    onToggleSubtitle: (Format?) -> Unit,
    onChooseTrack: (@C.TrackType Int, Format) -> Unit,
    onClearTrack: (@C.TrackType Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    var showSubtitleMenu by remember { mutableStateOf(false) }

    val subtitleTracks = remember(formats) {
        formats[C.TRACK_TYPE_TEXT] ?: emptyList()
    }

    val currentSubtitle = remember(selectedFormats) {
        selectedFormats[C.TRACK_TYPE_TEXT]
    }

    val isSubtitleEnabled = currentSubtitle != null

    Box(modifier = modifier) {
        // Subtitle toggle button
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .glassmorphismHaze(
                    backgroundColor = if (isSubtitleEnabled) {
                        NetflixTheme.NetflixRed.copy(alpha = 0.8f)
                    } else {
                        NetflixTheme.GlassLight.copy(alpha = 0.4f)
                    },
                    shape = CircleShape
                )
                .clickable {
                    if (subtitleTracks.isEmpty()) return@clickable

                    if (isSubtitleEnabled) {
                        // Turn off subtitles
                        onClearTrack(C.TRACK_TYPE_TEXT)
                    } else {
                        // Show subtitle menu or auto-select Norwegian
                        val norwegianSubtitle = subtitleTracks.find { format ->
                            format.language?.contains("no", ignoreCase = true) == true ||
                            format.language?.contains("nor", ignoreCase = true) == true ||
                            format.label?.contains("norw", ignoreCase = true) == true ||
                            format.label?.contains("norwegian", ignoreCase = true) == true
                        }

                        when {
                            norwegianSubtitle != null -> {
                                // Auto-select Norwegian subtitle
                                onChooseTrack(C.TRACK_TYPE_TEXT, norwegianSubtitle)
                            }
                            subtitleTracks.size == 1 -> {
                                // Only one subtitle available, select it
                                onChooseTrack(C.TRACK_TYPE_TEXT, subtitleTracks.first())
                            }
                            else -> {
                                // Show menu to choose
                                showSubtitleMenu = true
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isSubtitleEnabled) {
                    Icons.Rounded.ClosedCaption
                } else {
                    Icons.Rounded.ClosedCaptionDisabled
                },
                contentDescription = "Subtitles",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }

        // Subtitle selection menu
        AnimatedVisibility(
            visible = showSubtitleMenu,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            SubtitleSelectionMenu(
                subtitles = subtitleTracks,
                currentSubtitle = currentSubtitle,
                onSelectSubtitle = { format ->
                    onChooseTrack(C.TRACK_TYPE_TEXT, format)
                    showSubtitleMenu = false
                },
                onDismiss = { showSubtitleMenu = false },
                modifier = Modifier.padding(bottom = 56.dp)
            )
        }
    }
}

/**
 * Subtitle selection menu
 */
@Composable
private fun SubtitleSelectionMenu(
    subtitles: List<Format>,
    currentSubtitle: Format?,
    onSelectSubtitle: (Format) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current

    Box(
        modifier = modifier
            .width(200.dp)
            .clip(RoundedCornerShape(12.dp))
            .glassmorphismHaze(
                backgroundColor = NetflixTheme.NetflixDarkGray.copy(alpha = 0.95f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(spacing.small)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(spacing.extraSmall)
        ) {
            Text(
                text = "Select Subtitles",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = Color.White,
                modifier = Modifier.padding(spacing.small)
            )

            subtitles.forEach { format ->
                val isSelected = format.id == currentSubtitle?.id
                SubtitleMenuItem(
                    label = format.label ?: format.language ?: "Unknown",
                    language = format.language,
                    isSelected = isSelected,
                    onClick = { onSelectSubtitle(format) }
                )
            }

            Spacer(modifier = Modifier.height(spacing.extraSmall))

            // Off option
            SubtitleMenuItem(
                label = "Off",
                language = null,
                isSelected = currentSubtitle == null,
                onClick = onDismiss
            )
        }
    }
}

/**
 * Individual subtitle menu item
 */
@Composable
private fun SubtitleMenuItem(
    label: String,
    language: String?,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) {
                    NetflixTheme.NetflixRed.copy(alpha = 0.3f)
                } else {
                    Color.Transparent
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.medium, vertical = spacing.small)
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    ),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                language?.let {
                    Text(
                        text = it.uppercase(),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Rounded.ClosedCaption,
                    contentDescription = null,
                    tint = NetflixTheme.NetflixRed,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Helper function to get subtitle display name
 */
fun Format.getSubtitleDisplayName(): String {
    return when {
        label != null -> label!!
        language != null -> {
            when (language?.lowercase()) {
                "no", "nor", "nob", "nno" -> "Norwegian"
                "en", "eng" -> "English"
                "es", "spa" -> "Spanish"
                "fr", "fra" -> "French"
                "de", "deu", "ger" -> "German"
                "it", "ita" -> "Italian"
                "pt", "por" -> "Portuguese"
                "sv", "swe" -> "Swedish"
                "da", "dan" -> "Danish"
                "fi", "fin" -> "Finnish"
                else -> language!!.uppercase()
            }
        }
        else -> "Unknown"
    }
}
